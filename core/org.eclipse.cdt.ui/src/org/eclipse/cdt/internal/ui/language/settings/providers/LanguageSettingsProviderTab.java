/*******************************************************************************
 * Copyright (c) 2010, 2011 Andrew Gvozdev and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Andrew Gvozdev - Initial API and implementation
 *******************************************************************************/

package org.eclipse.cdt.internal.ui.language.settings.providers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.dialogs.PreferencesUtil;

import org.eclipse.cdt.core.language.settings.providers.ILanguageSettingsEditableProvider;
import org.eclipse.cdt.core.language.settings.providers.ILanguageSettingsProvider;
import org.eclipse.cdt.core.language.settings.providers.ILanguageSettingsProvidersKeeper;
import org.eclipse.cdt.core.language.settings.providers.LanguageSettingsManager;
import org.eclipse.cdt.core.language.settings.providers.LanguageSettingsSerializableProvider;
import org.eclipse.cdt.core.language.settings.providers.ScannerDiscoveryLegacySupport;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICResourceDescription;
import org.eclipse.cdt.ui.CDTSharedImages;
import org.eclipse.cdt.ui.CUIPlugin;
import org.eclipse.cdt.ui.dialogs.ICOptionPage;
import org.eclipse.cdt.ui.newui.AbstractCPropertyTab;
import org.eclipse.cdt.ui.newui.CDTPrefUtil;
import org.eclipse.cdt.utils.ui.controls.TabFolderLayout;

import org.eclipse.cdt.internal.ui.newui.Messages;
import org.eclipse.cdt.internal.ui.newui.StatusMessageLine;

/**
 * This tab presents language settings entries categorized by language
 * settings providers.
 *
 *@noinstantiate This class is not intended to be instantiated by clients.
 *@noextend This class is not intended to be subclassed by clients.
 */
public class LanguageSettingsProviderTab extends AbstractCPropertyTab {
	private static final String WORKSPACE_PREFERENCE_PAGE = "org.eclipse.cdt.ui.preferences.BuildSettingProperties"; //$NON-NLS-1$
	// TODO: generalize
	private static final String TEST_PLUGIN_ID_PATTERN = "org.eclipse.cdt.*.tests.*"; //$NON-NLS-1$

//	private static final String RENAME_STR = "Rename...";
//	private static final String RUN_STR = Messages.LanguageSettingsProviderTab_Run;
	private static final String CLEAR_STR = Messages.LanguageSettingsProviderTab_Clear;
	private static final String RESET_STR = "Reset";

//	private static final int BUTTON_RENAME = 0;
//	private static final int BUTTON_RUN = 0;
	private static final int BUTTON_CLEAR = 0;
	private static final int BUTTON_RESET = 1;
	// there is a separator instead of button #2
	private static final int BUTTON_MOVE_UP = 3;
	private static final int BUTTON_MOVE_DOWN = 4;

	private final static String[] BUTTON_LABELS_PROJECT = {
//		RENAME_STR,
//		RUN_STR,
		CLEAR_STR,
		RESET_STR,
		null,
		MOVEUP_STR,
		MOVEDOWN_STR,
	};

	private final static String[] BUTTON_LABELS_PREF = {
//		RENAME_STR,
//		RUN_STR,
		CLEAR_STR,
		RESET_STR,
	};

	private static final int[] DEFAULT_CONFIGURE_SASH_WEIGHTS = new int[] { 50, 50 };
	private SashForm sashFormConfigure;

	private Table tableProviders;
	private CheckboxTableViewer tableProvidersViewer;
	private Group groupOptionsPage;
	private ICOptionPage currentOptionsPage = null;
	private Composite compositeOptionsPage;

	private Button enableProvidersCheckBox;
	private StatusMessageLine fStatusLine;

	private Button sharedProviderCheckBox = null;
	private Link linkWorkspacePreferences = null;
	private Button projectStorageCheckBox = null;

	private Page_LanguageSettingsProviders masterPropertyPage = null;

	/**
	 * List of providers presented to the user.
	 * For global providers included in a configuration this contains references
	 * not raw providers.
	 */
	private List<ILanguageSettingsProvider> presentedProviders = null;
	private final Map<String, ICOptionPage> optionsPageMap = new HashMap<String, ICOptionPage>();
	private Map<String, List<ILanguageSettingsProvider>> initialProvidersByCfg = new HashMap<String, List<ILanguageSettingsProvider>>();

	private boolean initialEnablement = false;

	/**
	 * Returns current working copy of the provider. Creates one if it has not been created yet.
	 * Used by option pages when there is a need to modify the provider.
	 * Warning: Do not cache the result as the provider can be replaced at any time.
	 * @param providerId
	 *
	 * @return the provider
	 */
	public ILanguageSettingsProvider getWorkingCopy(String providerId) {
		ILanguageSettingsProvider provider = findProvider(providerId, presentedProviders);
		if (isWorkingCopy(provider))
			return provider;

		ILanguageSettingsProvider rawProvider = LanguageSettingsManager.getRawProvider(provider);
		Assert.isTrue(rawProvider instanceof ILanguageSettingsEditableProvider);

		ILanguageSettingsEditableProvider newProvider = LanguageSettingsManager.getProviderCopy((ILanguageSettingsEditableProvider)rawProvider, true);
		if (newProvider != null) {
			replaceSelectedProvider(newProvider);
		}

		return newProvider;
	}

	private boolean isReconfiguredForProject(ILanguageSettingsProvider provider) {
		String id = provider.getId();

		// check for the provider mismatch in configuration list vs. default list from the tool-chain
		ICConfigurationDescription cfgDescription = getConfigurationDescription();
		String[] defaultIds = ((ILanguageSettingsProvidersKeeper) cfgDescription).getDefaultLanguageSettingsProvidersIds();
		List<ILanguageSettingsProvider> providers = ((ILanguageSettingsProvidersKeeper) cfgDescription).getLanguageSettingProviders();
		if (defaultIds != null && Arrays.asList(defaultIds).contains(id) != providers.contains(provider)) {
			return true;
		}

		// check if "shared" flag matches default shared preference from extension point definition
		if (providers.contains(provider) && LanguageSettingsManager.isPreferShared(id) != LanguageSettingsManager.isWorkspaceProvider(provider)) {
			return true;
		}

		// check if configuration provider equals to the default one from extension point
		if (!LanguageSettingsManager.isWorkspaceProvider(provider) && !LanguageSettingsManager.isEqualExtensionProvider(provider, false)) {
			return true;
		}
		return false;
	}

	private boolean isEditedForProject(ILanguageSettingsProvider provider) {
		String id = provider.getId();

		// check for the provider mismatch in configuration list vs. initial list
		ICConfigurationDescription cfgDescription = getConfigurationDescription();
		List<ILanguageSettingsProvider> initialProviders = initialProvidersByCfg.get(cfgDescription.getId());
		List<ILanguageSettingsProvider> providers = ((ILanguageSettingsProvidersKeeper) cfgDescription).getLanguageSettingProviders();
		ILanguageSettingsProvider initialProvider = findProvider(id, initialProviders);
		if ((initialProvider != null) != providers.contains(provider)) {
			return true;
		}

		// check if "shared" flag matches that of initial provider
		if (providers.contains(provider) && LanguageSettingsManager.isWorkspaceProvider(initialProvider) != LanguageSettingsManager.isWorkspaceProvider(provider)) {
			return true;
		}

		// check if configuration provider equals to the initial one
		if (!LanguageSettingsManager.isWorkspaceProvider(provider) && !provider.equals(initialProvider)) {
			return true;
		}
		return false;
	}

	private class ProvidersTableLabelProvider extends LanguageSettingsProvidersLabelProvider {
		@Override
		protected String[] getOverlayKeys(ILanguageSettingsProvider provider) {
			if (provider.getName() == null) {
				String[] overlayKeys = new String[5];
				overlayKeys[IDecoration.TOP_RIGHT] = CDTSharedImages.IMG_OVR_ERROR;
				return overlayKeys;
			}


			String[] overlayKeys = super.getOverlayKeys(provider);

			if (page.isForProject()) {
				if (isEditedForProject(provider)) {
					overlayKeys[IDecoration.TOP_RIGHT] = CDTSharedImages.IMG_OVR_EDITED;
				} else if (!LanguageSettingsManager.getExtensionProviderIds().contains(provider.getId())) {
					overlayKeys[IDecoration.TOP_RIGHT] = CDTSharedImages.IMG_OVR_USER;
				} else if (isReconfiguredForProject(provider)) {
					overlayKeys[IDecoration.TOP_RIGHT] = CDTSharedImages.IMG_OVR_SETTING;
				}
			} else if (page.isForPrefs()) {
				if (isWorkingCopy(provider) && !provider.equals(LanguageSettingsManager.getRawProvider(LanguageSettingsManager.getWorkspaceProvider(provider.getId())))) {
					overlayKeys[IDecoration.TOP_RIGHT] = CDTSharedImages.IMG_OVR_EDITED;
				} else if (!LanguageSettingsManager.getExtensionProviderIds().contains(provider.getId())) {
					overlayKeys[IDecoration.TOP_RIGHT] = CDTSharedImages.IMG_OVR_USER;
				} else {
					ILanguageSettingsProvider rawProvider = LanguageSettingsManager.getRawProvider(provider);
					if (rawProvider instanceof ILanguageSettingsEditableProvider && !LanguageSettingsManager.isEqualExtensionProvider(rawProvider, false)) {
						overlayKeys[IDecoration.TOP_RIGHT] = CDTSharedImages.IMG_OVR_SETTING;
					}
				}
			}

			return overlayKeys;
		}

		@Override
		public String getText(Object element) {
			// AG TODO - address duplication with super.getText()
			if (element instanceof ILanguageSettingsProvider) {
				ILanguageSettingsProvider provider = (ILanguageSettingsProvider) element;
				String name = provider.getName();
				if (name != null) {
					if (page.isForPrefs() || isPresentedAsShared(provider)) {
						name = name + "   [ Shared ]";
					}
					return name;
				}
				String id = provider.getId();
				return "[ Not accessible id="+id+" ]";
			}
			return super.getText(element);
		}


	}

	/**
	 * Shortcut for getting the current resource for the property page.
	 */
	private IResource getResource() {
		return (IResource)page.getElement();
	}

	/**
	 * Shortcut for getting the current configuration description.
	 */
	private ICConfigurationDescription getConfigurationDescription() {
		if (page.isForPrefs())
			return null;

		ICConfigurationDescription cfgDescription = getResDesc().getConfiguration();
			return cfgDescription;
		}

	/**
	 * Shortcut for getting the currently selected provider.
	 * Do not use if you need to change provider's settings, use {@link #getWorkingCopy(String)}.
	 */
	private ILanguageSettingsProvider getSelectedProvider() {
		ILanguageSettingsProvider provider = null;

		int pos = tableProviders.getSelectionIndex();
		if (pos >= 0 && pos<tableProviders.getItemCount()) {
			provider = (ILanguageSettingsProvider)tableProvidersViewer.getElementAt(pos);
		}
		return provider;
	}

	private void trackInitialSettings() {
		if (page.isForProject()) {
			ICConfigurationDescription[] cfgDescriptions = page.getCfgsEditable();
			for (ICConfigurationDescription cfgDescription : cfgDescriptions) {
				if (cfgDescription instanceof ILanguageSettingsProvidersKeeper) {
					String cfgId = cfgDescription.getId();
					List<ILanguageSettingsProvider> initialProviders = ((ILanguageSettingsProvidersKeeper) cfgDescription).getLanguageSettingProviders();
					initialProvidersByCfg.put(cfgId, initialProviders);
				}
			}
			initialEnablement = ScannerDiscoveryLegacySupport.isLanguageSettingsProvidersFunctionalityEnabled(page.getProject());
		}
	}

	@Override
	public void createControls(Composite parent) {
		super.createControls(parent);
		usercomp.setLayout(new GridLayout());
		GridData gd = (GridData) usercomp.getLayoutData();
		// Discourage settings entry table from trying to show all its items at once, see bug 264330
		gd.heightHint =1;

		if (page instanceof Page_LanguageSettingsProviders) {
			masterPropertyPage = (Page_LanguageSettingsProviders) page;
		}

		trackInitialSettings();

		// SashForms for each mode
		createConfigureSashForm();

		// Status line
		fStatusLine = new StatusMessageLine(usercomp, SWT.LEFT, 2);

		if (page.isForPrefs()) {
			initButtons(BUTTON_LABELS_PREF);

		} else {
			// "I want to try new scanner discovery" temporary checkbox
			enableProvidersCheckBox = setupCheck(usercomp, Messages.CDTMainWizardPage_TrySD90, 2, GridData.FILL_HORIZONTAL);
			enableProvidersCheckBox.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					boolean enabled = enableProvidersCheckBox.getSelection();
					if (masterPropertyPage!=null)
						masterPropertyPage.setLanguageSettingsProvidersEnabled(enabled);

					// AG TODO - if not enabled reset providers to MBS provider only
					if (!enabled) {
						ICConfigurationDescription cfgDescription = getConfigurationDescription();
						if (cfgDescription instanceof ILanguageSettingsProvidersKeeper) {
							((ILanguageSettingsProvidersKeeper) cfgDescription).setLanguageSettingProviders(ScannerDiscoveryLegacySupport.getDefaultProvidersLegacy());
							updateData(getResDesc());
						}

					}
					enableControls(enabled);
					updateStatusLine();
				}
			});

			if (masterPropertyPage!=null)
				enableProvidersCheckBox.setSelection(masterPropertyPage.isLanguageSettingsProvidersEnabled());
			else
				enableProvidersCheckBox.setSelection(ScannerDiscoveryLegacySupport.isLanguageSettingsProvidersFunctionalityEnabled(page.getProject()));
			// display but disable the checkbox for file/folder resource
			enableProvidersCheckBox.setEnabled(page.isForProject());
			enableControls(enableProvidersCheckBox.getSelection());

			initButtons(BUTTON_LABELS_PROJECT);
		}
		updateData(getResDesc());
	}

	private void createConfigureSashForm() {
		// SashForm for Configure
		sashFormConfigure = new SashForm(usercomp, SWT.VERTICAL);
		GridLayout layout = new GridLayout();
		sashFormConfigure.setLayout(layout);

		// Providers table
		Composite compositeSashForm = new Composite(sashFormConfigure, SWT.BORDER | SWT.SINGLE);
		compositeSashForm.setLayout(new GridLayout());

		// items checkboxes  only for project properties page
		tableProviders = new Table(compositeSashForm, page.isForPrefs() ? SWT.NONE : SWT.CHECK);
		tableProviders.setLayoutData(new GridData(GridData.FILL_BOTH));
		tableProviders.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				displaySelectedOptionPage();
				updateButtons();
			}
		});
		tableProvidersViewer = new CheckboxTableViewer(tableProviders);
		tableProvidersViewer.setContentProvider(new ArrayContentProvider());
		tableProvidersViewer.setLabelProvider(new ProvidersTableLabelProvider());

		tableProvidersViewer.addCheckStateListener(new ICheckStateListener() {
			@Override
			public void checkStateChanged(CheckStateChangedEvent event) {
				// TODO: clean-up - too many manipulations in this method

				ILanguageSettingsProvider provider = (ILanguageSettingsProvider) event.getElement();
				saveCheckedProviders(provider);

				int pos = presentedProviders.indexOf(provider);
				tableProviders.setSelection(pos);

				if (event.getChecked()) {
					if (LanguageSettingsManager.isWorkspaceProvider(provider)) {
						if (!LanguageSettingsManager.isPreferShared(provider.getId())) {
							// Switch to non-shared provider instance
							ILanguageSettingsProvider rawProvider = LanguageSettingsManager.getRawProvider(provider);
							if (rawProvider instanceof ILanguageSettingsEditableProvider) {
								ILanguageSettingsEditableProvider newProvider = LanguageSettingsManager.getProviderCopy((ILanguageSettingsEditableProvider) rawProvider, false);
								if (newProvider != null) {
									provider = newProvider;
									replaceSelectedProvider(provider);
									ICConfigurationDescription cfgDescription = getConfigurationDescription();
									initializeOptionsPage(provider, cfgDescription);
								}
							}
						}
					}
				} else {
					if (!LanguageSettingsManager.isWorkspaceProvider(provider)) {
						provider = LanguageSettingsManager.getWorkspaceProvider(provider.getId());
						replaceSelectedProvider(provider);
						ICConfigurationDescription cfgDescription = getConfigurationDescription();
						initializeOptionsPage(provider, cfgDescription);
					}
				}

				displaySelectedOptionPage();
				tableProvidersViewer.update(provider, null);
				updateButtons();
			}});

		createOptionsControl();

		sashFormConfigure.setWeights(DEFAULT_CONFIGURE_SASH_WEIGHTS);
		enableSashForm(sashFormConfigure, true);
	}

	private Link createLinkToPreferences(final Composite parent, int span) {
		Link link = new Link(parent, SWT.NONE);
		GridData gd = new GridData();
		gd.horizontalSpan = span;
		link.setLayoutData(gd);

		link.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				// Use event.text to tell which link was used
				PreferencesUtil.createPreferenceDialogOn(parent.getShell(), WORKSPACE_PREFERENCE_PAGE, null, null).open();
			}
		});

		return link;
	}

	// Called from globalProviderCheckBox listener
	private ILanguageSettingsProvider toggleGlobalProvider(ILanguageSettingsProvider oldProvider, boolean toGlobal) {
		ILanguageSettingsProvider newProvider = null;

		String id = oldProvider.getId();
		if (toGlobal) {
			newProvider = LanguageSettingsManager.getWorkspaceProvider(id);
		} else {
			// Local provider instance chosen
			try {
				ILanguageSettingsProvider rawProvider = LanguageSettingsManager.getRawProvider(oldProvider);
				if (rawProvider instanceof ILanguageSettingsEditableProvider) {
					newProvider = ((ILanguageSettingsEditableProvider) rawProvider).cloneShallow();
				}
			} catch (CloneNotSupportedException e) {
				CUIPlugin.log("Error cloning provider " + oldProvider.getId(), e);
			}
		}
		if (newProvider!=null) {
			replaceSelectedProvider(newProvider);

			ICConfigurationDescription cfgDescription = getConfigurationDescription();
			initializeOptionsPage(newProvider, cfgDescription);
			displaySelectedOptionPage();
		} else {
			newProvider = oldProvider;
		}

		return newProvider;
	}

	private void replaceSelectedProvider(ILanguageSettingsProvider newProvider) {
		int pos = tableProviders.getSelectionIndex();
		presentedProviders.set(pos, newProvider);
		tableProvidersViewer.setInput(presentedProviders);
		tableProviders.setSelection(pos);

		ICConfigurationDescription cfgDescription = null;
		if (!page.isForPrefs()) {
			cfgDescription = getConfigurationDescription();

			if (cfgDescription instanceof ILanguageSettingsProvidersKeeper) {
				List<ILanguageSettingsProvider> cfgProviders = new ArrayList<ILanguageSettingsProvider>(((ILanguageSettingsProvidersKeeper) cfgDescription).getLanguageSettingProviders());
				pos = getProviderIndex(newProvider.getId(), cfgProviders);
				if (pos >= 0) {
					cfgProviders.set(pos, newProvider);
					((ILanguageSettingsProvidersKeeper) cfgDescription).setLanguageSettingProviders(cfgProviders);
					tableProvidersViewer.setCheckedElements(cfgProviders.toArray(new ILanguageSettingsProvider[0]));
				}
			}
		}
		refreshItem(newProvider);
	}

	public void refreshItem(ILanguageSettingsProvider provider) {
		tableProvidersViewer.refresh(provider);
		updateButtons();
	}

	private void createOptionsControl() {
		groupOptionsPage = new Group(sashFormConfigure, SWT.SHADOW_ETCHED_IN);
		groupOptionsPage.setText("Language Settings Provider Options");
		groupOptionsPage.setLayout(new GridLayout(2, false));

		if (!page.isForPrefs()) {
			if (sharedProviderCheckBox==null) {
				sharedProviderCheckBox = new Button(groupOptionsPage, SWT.CHECK);
				sharedProviderCheckBox.setText("Share setting entries between projects (global provider)");
				sharedProviderCheckBox.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						boolean isGlobal = sharedProviderCheckBox.getSelection();
						ILanguageSettingsProvider provider = getSelectedProvider();
						if (isGlobal != LanguageSettingsManager.isWorkspaceProvider(provider)) {
							provider = toggleGlobalProvider(provider, isGlobal);
						}
						projectStorageCheckBox.setSelection(provider instanceof LanguageSettingsSerializableProvider
								&& LanguageSettingsManager.isStoringEntriesInProjectArea((LanguageSettingsSerializableProvider) provider));
					}

					@Override
					public void widgetDefaultSelected(SelectionEvent e) {
						widgetSelected(e);
					}

				});
			}

			if (projectStorageCheckBox == null) {
				projectStorageCheckBox = new Button(groupOptionsPage, SWT.CHECK);
				projectStorageCheckBox.setText("Store entries in project settings folder (easing project miration)");
				projectStorageCheckBox.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						boolean isWithProject = projectStorageCheckBox.getSelection();
						ILanguageSettingsProvider provider = getWorkingCopy(getSelectedProvider().getId());
						Assert.isTrue(provider instanceof LanguageSettingsSerializableProvider);
						LanguageSettingsManager.setStoringEntriesInProjectArea((LanguageSettingsSerializableProvider) provider, isWithProject);
						refreshItem(provider);
					}

					@Override
					public void widgetDefaultSelected(SelectionEvent e) {
						widgetSelected(e);
					}

				});

				linkWorkspacePreferences = createLinkToPreferences(groupOptionsPage, 2);
			}
		}

		compositeOptionsPage = new Composite(groupOptionsPage, SWT.NONE);
		compositeOptionsPage.setLayout(new TabFolderLayout());
	}

	private void enableSashForm(SashForm sashForm, boolean enable) {
		sashForm.setVisible(enable);
		// Some of woodoo to fill properties page vertically and still keep right border visible in preferences
		GridData gd = new GridData(enable || page.isForPrefs() ? GridData.FILL_BOTH : SWT.NONE);
		gd.horizontalSpan = 2;
		gd.heightHint = enable ? SWT.DEFAULT : 0;
		sashForm.setLayoutData(gd);
	}

	private void enableControls(boolean enable) {
		sashFormConfigure.setEnabled(enable);
		tableProviders.setEnabled(enable);
		compositeOptionsPage.setEnabled(enable);

		buttoncomp.setEnabled(enable);

		if (enable) {
			displaySelectedOptionPage();
		} else {
			if (currentOptionsPage != null) {
				currentOptionsPage.setVisible(false);
			}
			disableButtons();
		}
	}

	/**
	 * Populate provider tables and their option pages which are used in Configure mode
	 */
	private void updateProvidersTable() {
		ILanguageSettingsProvider selectedProvider = getSelectedProvider();
		String selectedId = selectedProvider!=null ? selectedProvider.getId() : null;

		// update viewer if the list of providers changed
		int pos = tableProviders.getSelectionIndex();
		tableProvidersViewer.setInput(presentedProviders);
		tableProviders.setSelection(pos);

		ICConfigurationDescription cfgDescription = getConfigurationDescription();
		if (cfgDescription instanceof ILanguageSettingsProvidersKeeper) {
			List<ILanguageSettingsProvider> cfgProviders = ((ILanguageSettingsProvidersKeeper) cfgDescription).getLanguageSettingProviders();
			tableProvidersViewer.setCheckedElements(cfgProviders.toArray(new ILanguageSettingsProvider[0]));
		}

		if (selectedId!=null) {
			for (int i=0; i<presentedProviders.size(); i++) {
				if (selectedId.equals(presentedProviders.get(i).getId())) {
					tableProviders.setSelection(i);
					break;
				}
			}
		}

		optionsPageMap.clear();
		for (ILanguageSettingsProvider provider : presentedProviders) {
			initializeOptionsPage(provider, cfgDescription);
		}

		displaySelectedOptionPage();
	}

	private void initializeProviders() {
		// The providers list is formed to consist of configuration providers (checked elements on top of the table)
		// and after that other providers which could be possible added (unchecked) sorted by name.

		List<String> idsList = new ArrayList<String>();

		List<ILanguageSettingsProvider> providers;
		ICConfigurationDescription cfgDescription = getConfigurationDescription();
		if (cfgDescription instanceof ILanguageSettingsProvidersKeeper) {
			providers = new ArrayList<ILanguageSettingsProvider>(((ILanguageSettingsProvidersKeeper) cfgDescription).getLanguageSettingProviders());
			for (ILanguageSettingsProvider provider : providers) {
				idsList.add(provider.getId());
			}
		} else {
			providers =  new ArrayList<ILanguageSettingsProvider>();
		}

		List<ILanguageSettingsProvider> allAvailableProvidersSet = LanguageSettingsManager.getWorkspaceProviders();
		sortByName(allAvailableProvidersSet);

		for (ILanguageSettingsProvider provider : allAvailableProvidersSet) {
			String id = provider.getId();
			if (!idsList.contains(id)) {
				providers.add(provider);
				idsList.add(id);
			}
		}

		// renders better when using temporary
		presentedProviders = providers;

		int pos = tableProviders.getSelectionIndex();
		tableProvidersViewer.setInput(presentedProviders);
		tableProviders.setSelection(pos);
	}

	private void sortByName(List<ILanguageSettingsProvider> providers) {
		// ensure sorting by name all unchecked providers
		Collections.sort(providers, new Comparator<ILanguageSettingsProvider>() {
			@Override
			public int compare(ILanguageSettingsProvider prov1, ILanguageSettingsProvider prov2) {
				Boolean isTest1 = prov1.getId().matches(TEST_PLUGIN_ID_PATTERN);
				Boolean isTest2 = prov2.getId().matches(TEST_PLUGIN_ID_PATTERN);
				int result = isTest1.compareTo(isTest2);
				if (result == 0) {
					String name1 = prov1.getName();
					String name2 = prov2.getName();
					if (name1 != null && name2 != null) {
						result = name1.compareTo(name2);
					}
				}
				return result;
			}
		});
	}

	private ICOptionPage createOptionsPage(ILanguageSettingsProvider provider, ICConfigurationDescription cfgDescription) {
		ICOptionPage optionsPage = null;
		if (provider!=null) {
			ILanguageSettingsProvider rawProvider = LanguageSettingsManager.getRawProvider(provider);
			if (rawProvider!=null) {
				optionsPage = LanguageSettingsProviderAssociationManager.createOptionsPage(rawProvider);
			}

			if (optionsPage instanceof AbstractLanguageSettingProviderOptionPage) {
				((AbstractLanguageSettingProviderOptionPage)optionsPage).init(this, provider.getId());
			}
		}

		return optionsPage;
	}

	private void initializeOptionsPage(ILanguageSettingsProvider provider, ICConfigurationDescription cfgDescription) {
		ICOptionPage optionsPage = createOptionsPage(provider, cfgDescription);

		if (optionsPage!=null) {
			ILanguageSettingsProvider rawProvider = LanguageSettingsManager.getRawProvider(provider);
			boolean isEditableForProject = page.isForProject() && provider instanceof ILanguageSettingsEditableProvider;
			boolean isEditableForPrefs = page.isForPrefs() && rawProvider instanceof ILanguageSettingsEditableProvider;
			boolean isEditable = isEditableForProject || isEditableForPrefs;
			compositeOptionsPage.setEnabled(isEditable);

			String id = (provider!=null) ? provider.getId() : null;
			optionsPageMap.put(id, optionsPage);
			optionsPage.setContainer(page);
			optionsPage.createControl(compositeOptionsPage);
			optionsPage.setVisible(false);
			compositeOptionsPage.layout(true);
		}
	}

	private void displaySelectedOptionPage() {
		if (currentOptionsPage != null) {
			currentOptionsPage.setVisible(false);
		}

		ILanguageSettingsProvider provider = getSelectedProvider();
		String id = (provider!=null) ? provider.getId() : null;

		boolean isGlobal = LanguageSettingsManager.isWorkspaceProvider(provider);
		ILanguageSettingsProvider rawProvider = LanguageSettingsManager.getRawProvider(provider);

		currentOptionsPage = optionsPageMap.get(id);

		boolean isChecked = tableProvidersViewer.getChecked(provider);
		if (!page.isForPrefs()) {
			boolean isRawProviderEditable = rawProvider instanceof ILanguageSettingsEditableProvider;

			sharedProviderCheckBox.setSelection(isPresentedAsShared(provider));
			sharedProviderCheckBox.setEnabled(isChecked && isRawProviderEditable);
			sharedProviderCheckBox.setVisible(provider!=null);

			projectStorageCheckBox.setEnabled(!isGlobal);
			projectStorageCheckBox.setVisible(rawProvider instanceof LanguageSettingsSerializableProvider);
			projectStorageCheckBox.setSelection(provider instanceof LanguageSettingsSerializableProvider
					&& LanguageSettingsManager.isStoringEntriesInProjectArea((LanguageSettingsSerializableProvider) provider));

			boolean needPreferencesLink=isGlobal && currentOptionsPage!=null;
			// TODO: message
			final String linkMsg = needPreferencesLink ? "Options of global providers below can be changed in <a href=\"workspace\">Workspace Settings</a>, Discovery Tab." : "";
			linkWorkspacePreferences.setText(linkMsg);
			linkWorkspacePreferences.pack();
			linkWorkspacePreferences.setEnabled(isChecked);
		}

		if (currentOptionsPage != null) {
			boolean isEditableForProject = page.isForProject() && provider instanceof ILanguageSettingsEditableProvider;
			boolean isEditableForPrefs = page.isForPrefs() && rawProvider instanceof ILanguageSettingsEditableProvider;
			boolean isEditable = isEditableForProject || isEditableForPrefs;
			currentOptionsPage.getControl().setEnabled(isEditable);
			currentOptionsPage.setVisible(true);

			compositeOptionsPage.setEnabled(isEditable);
//			compositeOptionsPage.layout(true);
		}
	}

	/**
	 * Checks if the provider should be presented as shared. Unchecked providers are shown as non-shared
	 * if they are defined as non-shared in extension point even if in fact shared instance is used to display
	 * the options page.
	 */
	private boolean isPresentedAsShared(ILanguageSettingsProvider provider) {
		ICConfigurationDescription cfgDescription = getConfigurationDescription();
		List<ILanguageSettingsProvider> providers = ((ILanguageSettingsProvidersKeeper) cfgDescription).getLanguageSettingProviders();
		return LanguageSettingsManager.isWorkspaceProvider(provider) &&
				( providers.contains(provider) || LanguageSettingsManager.isPreferShared(provider.getId()) );
	}


	private void saveCheckedProviders(Object selectedElement) {
		if (page.isForProject()) {
			Object[] checked = tableProvidersViewer.getCheckedElements();
			List<ILanguageSettingsProvider> providers = new ArrayList<ILanguageSettingsProvider>(checked.length);
			for (Object element : checked) {
				ILanguageSettingsProvider provider = (ILanguageSettingsProvider)element;
				providers.add(provider);
			}
			ICConfigurationDescription cfgDescription = getConfigurationDescription();
			if (cfgDescription instanceof ILanguageSettingsProvidersKeeper) {
				((ILanguageSettingsProvidersKeeper) cfgDescription).setLanguageSettingProviders(providers);

				if (selectedElement!=null) {
					tableProvidersViewer.update(selectedElement, null);
					if (selectedElement instanceof ILanguageSettingsProvider) {
						ILanguageSettingsProvider selectedProvider = (ILanguageSettingsProvider) selectedElement;
						initializeOptionsPage(selectedProvider, cfgDescription);
						displaySelectedOptionPage();
					}
				}
			}
		}
	}

	private void disableButtons() {
//		buttonSetEnabled(BUTTON_RENAME, false);
//		buttonSetEnabled(BUTTON_RUN, false);
		buttonSetEnabled(BUTTON_CLEAR, false);
		buttonSetEnabled(BUTTON_RESET, false);
		buttonSetEnabled(BUTTON_MOVE_UP, false);
		buttonSetEnabled(BUTTON_MOVE_DOWN, false);
//		buttonSetEnabled(BUTTON_CONFIGURE, false);
	}

	/**
	 * Updates state for all buttons. Called when table selection changes.
	 */
	@Override
	protected void updateButtons() {
		ILanguageSettingsProvider provider = getSelectedProvider();
		boolean isProviderSelected = provider != null;
		boolean canForWorkspace = isProviderSelected && page.isForPrefs();
		boolean canForProject = isProviderSelected && page.isForProject();

		int pos = tableProviders.getSelectionIndex();
		int count = tableProviders.getItemCount();
		int last = count - 1;
		boolean isRangeOk = pos >= 0 && pos <= last;

		ILanguageSettingsProvider rawProvider = LanguageSettingsManager.getRawProvider(provider);
		boolean isAllowedClearing = rawProvider instanceof ILanguageSettingsEditableProvider && rawProvider instanceof LanguageSettingsSerializableProvider
				&& LanguageSettingsProviderAssociationManager.isAllowedToClear(rawProvider);

		boolean canClear = isAllowedClearing && (canForWorkspace || (canForProject && !LanguageSettingsManager.isWorkspaceProvider(provider)));
		if (rawProvider instanceof LanguageSettingsSerializableProvider) {
			canClear = canClear && !((LanguageSettingsSerializableProvider)rawProvider).isEmpty();
		}

		boolean canReset = (canForProject && isReconfiguredForProject(provider)) ||
				(canForWorkspace &&
						(rawProvider instanceof ILanguageSettingsEditableProvider
								&& !LanguageSettingsManager.isEqualExtensionProvider(rawProvider, false))
								&& ( LanguageSettingsManager.getExtensionProviderIds().contains(rawProvider.getId()) )
						);

		boolean canMoveUp = canForProject && isRangeOk && pos!=0;
		boolean canMoveDown = canForProject && isRangeOk && pos!=last;

//		buttonSetEnabled(BUTTON_RENAME, false);
//		buttonSetEnabled(BUTTON_RUN, false);
		buttonSetEnabled(BUTTON_CLEAR, canClear);
		buttonSetEnabled(BUTTON_RESET, canReset);
		buttonSetEnabled(BUTTON_MOVE_UP, canMoveUp);
		buttonSetEnabled(BUTTON_MOVE_DOWN, canMoveDown);
	}

	/**
	 * Displays warning message - if any - for selected language settings entry.
	 */
	private void updateStatusLine() {
//		IStatus status=null;
//		fStatusLine.setErrorStatus(status);
	}

	/**
	 * Handle buttons
	 */
	@Override
	public void buttonPressed(int buttonIndex) {
		ILanguageSettingsProvider selectedProvider = getSelectedProvider();

		switch (buttonIndex) {
//		case BUTTON_RENAME:
//			performRename(selectedProvider);
//			break;
//		case BUTTON_RUN:
//			performRun(selectedProvider);
//			break;
		case BUTTON_CLEAR:
			performClear(selectedProvider);
			break;
		case BUTTON_RESET:
			performReset(selectedProvider);
			break;
		case BUTTON_MOVE_UP:
			performMoveUp(selectedProvider);
			break;
		case BUTTON_MOVE_DOWN:
			performMoveDown(selectedProvider);
			break;
		default:
		}
	}

	private void performClear(ILanguageSettingsProvider selectedProvider) {
		if (isWorkingCopy(selectedProvider)) {
			if (selectedProvider instanceof LanguageSettingsSerializableProvider) {
				LanguageSettingsSerializableProvider editableProvider = (LanguageSettingsSerializableProvider) selectedProvider;
				editableProvider.clear();
				tableProvidersViewer.update(selectedProvider, null);
			}
		} else {
			ILanguageSettingsProvider rawProvider = LanguageSettingsManager.getRawProvider(selectedProvider);
			if (rawProvider instanceof ILanguageSettingsEditableProvider) {
				ILanguageSettingsEditableProvider newProvider = LanguageSettingsManager.getProviderCopy((ILanguageSettingsEditableProvider) rawProvider, false);
				if (newProvider != null) {
					replaceSelectedProvider(newProvider);
					ICConfigurationDescription cfgDescription = getConfigurationDescription();
					initializeOptionsPage(newProvider, cfgDescription);
					displaySelectedOptionPage();
				}
			}
		}
		updateButtons();
	}

	private void performReset(ILanguageSettingsProvider selectedProvider) {
		String id = selectedProvider.getId();
		ILanguageSettingsProvider newProvider = null;
		ICConfigurationDescription cfgDescription = getConfigurationDescription();

		if (page.isForPrefs()) {
			newProvider = LanguageSettingsManager.getExtensionProviderCopy(id, true);
			if (newProvider == null) {
				newProvider = LanguageSettingsManager.getWorkspaceProvider(id);
			}
			replaceSelectedProvider(newProvider);
		} else {
			List<ILanguageSettingsProvider> providers = ((ILanguageSettingsProvidersKeeper) cfgDescription).getLanguageSettingProviders();
			String[] defaultIds = ((ILanguageSettingsProvidersKeeper) cfgDescription).getDefaultLanguageSettingsProvidersIds();
			boolean isDefault = Arrays.asList(defaultIds).contains(id);

			if (isDefault) {
				if (!LanguageSettingsManager.isPreferShared(id)) {
					newProvider = LanguageSettingsManager.getExtensionProviderCopy(id, true);
				}
				if (newProvider == null) {
					newProvider = LanguageSettingsManager.getWorkspaceProvider(id);
				}
				if (providers.contains(selectedProvider)) {
					// replace provider in the cfg list
					replaceSelectedProvider(newProvider);
				} else {
					// add provider to the cfg list
					replaceSelectedProvider(newProvider);
					tableProvidersViewer.setChecked(newProvider, true);
					saveCheckedProviders(newProvider);
					updateProvidersTable();
					refreshItem(newProvider);
				}
			} else {
				if (providers.contains(selectedProvider)) {
					// remove provider from the cfg list
					newProvider = LanguageSettingsManager.getWorkspaceProvider(id);
					replaceSelectedProvider(newProvider);
					tableProvidersViewer.setChecked(newProvider, false);
					saveCheckedProviders(newProvider);
					updateProvidersTable();
					refreshItem(newProvider);
				}
			}
		}

		initializeOptionsPage(newProvider, cfgDescription);
		displaySelectedOptionPage();
		updateButtons();
	}

	private boolean isWorkingCopy(ILanguageSettingsProvider provider) {
		boolean isWorkingCopy = false;
		if (page.isForPrefs()) {
			isWorkingCopy = ! LanguageSettingsManager.isWorkspaceProvider(provider);
		} else {
			if (!LanguageSettingsManager.isWorkspaceProvider(provider)) {
				ICConfigurationDescription cfgDescription = getConfigurationDescription();
				List<ILanguageSettingsProvider> initialProviders = initialProvidersByCfg.get(cfgDescription.getId());
				isWorkingCopy = ! initialProviders.contains(provider);
			}

		}
		return isWorkingCopy;
	}

	private void performMoveUp(ILanguageSettingsProvider selectedProvider) {
		int pos = presentedProviders.indexOf(selectedProvider);
		if (pos > 0) {
			moveProvider(pos, pos-1);
		}
	}

	private void performMoveDown(ILanguageSettingsProvider selectedProvider) {
		int pos = presentedProviders.indexOf(selectedProvider);
		int last = presentedProviders.size() - 1;
		if (pos >= 0 && pos < last) {
			moveProvider(pos, pos+1);
		}
	}

	private void moveProvider(int oldPos, int newPos) {
		Collections.swap(presentedProviders, oldPos, newPos);

		updateProvidersTable();
		tableProviders.setSelection(newPos);

		saveCheckedProviders(null);
		updateButtons();
	}

	/**
	 * Called when configuration changed
	 */
	@Override
	public void updateData(ICResourceDescription rcDes) {
		if (!canBeVisible())
			return;

		if (rcDes!=null) {
			if (page.isMultiCfg()) {
				setAllVisible(false, null);
				return;
			} else {
				setAllVisible(true, null);
			}

			if (enableProvidersCheckBox!=null && masterPropertyPage!=null) {
				boolean enabled = masterPropertyPage.isLanguageSettingsProvidersEnabled();
				enableProvidersCheckBox.setSelection(enabled);
				enableControls(enabled);
			}
		}

		// for Preference page initialize providers list just once as no configuration here to change
		// and re-initializing could ruins modified providers in case of switching tabs or pages
		if (!page.isForPrefs() || presentedProviders==null) {
			initializeProviders();
		}
		updateProvidersTable();
		updateButtons();
	}

	@Override
	protected void performDefaults() {
		if (page.isForProject() && (enableProvidersCheckBox==null || enableProvidersCheckBox.getSelection() == false))
			return;

		if (page.isForPrefs() || page.isForProject()) {
			if (MessageDialog.openQuestion(usercomp.getShell(),
					Messages.LanguageSettingsProviderTab_TitleResetProviders,
					Messages.LanguageSettingsProviderTab_AreYouSureToResetProviders)) {

				if (page.isForProject()) {
					ICConfigurationDescription cfgDescription = getConfigurationDescription();
					if (cfgDescription instanceof ILanguageSettingsProvidersKeeper) {
						List<ILanguageSettingsProvider> cfgProviders = new ArrayList<ILanguageSettingsProvider>(((ILanguageSettingsProvidersKeeper) cfgDescription).getLanguageSettingProviders());
						String[] defaultIds = ((ILanguageSettingsProvidersKeeper) cfgDescription).getDefaultLanguageSettingsProvidersIds();

						List<ILanguageSettingsProvider> newProviders = new ArrayList<ILanguageSettingsProvider>(defaultIds.length);
						for (String id : defaultIds) {
							boolean preferShared = LanguageSettingsManager.isPreferShared(id);
							ILanguageSettingsProvider newProvider = null;
							if (!preferShared) {
								newProvider = LanguageSettingsManager.getExtensionProviderCopy(id, true);
							}
							if (newProvider == null) {
								newProvider = LanguageSettingsManager.getWorkspaceProvider(id);
							}
							newProviders.add(newProvider);
						}

						if (!cfgProviders.equals(newProviders)) {
							((ILanguageSettingsProvidersKeeper) cfgDescription).setLanguageSettingProviders(newProviders);
						}
					}

				} else if (page.isForPrefs()) {
					presentedProviders = new ArrayList<ILanguageSettingsProvider>();
					for (ILanguageSettingsProvider provider : LanguageSettingsManager.getWorkspaceProviders()) {
						if (!LanguageSettingsManager.isEqualExtensionProvider(provider, true)) {
							ILanguageSettingsProvider extProvider = LanguageSettingsManager.getExtensionProviderCopy(provider.getId(), true);
							if (extProvider != null) {
								provider = extProvider;
							}
						}
						presentedProviders.add(provider);
					}
					sortByName(presentedProviders);
				}
			}

			updateData(getResDesc());
		}
	}

	@Override
	protected void performApply(ICResourceDescription srcRcDescription, ICResourceDescription destRcDescription) {
//		informOptionPages(true);

		if (page.isForPrefs()) {
			try {
				LanguageSettingsManager.setWorkspaceProviders(presentedProviders);
			} catch (CoreException e) {
				CUIPlugin.log("Error serializing workspace language settings providers", e);
			}
		} else {
			IResource rc = getResource();

			ICConfigurationDescription sd = srcRcDescription.getConfiguration();
			ICConfigurationDescription dd = destRcDescription.getConfiguration();
			if (sd instanceof ILanguageSettingsProvidersKeeper && dd instanceof ILanguageSettingsProvidersKeeper) {
				List<ILanguageSettingsProvider> newProviders = ((ILanguageSettingsProvidersKeeper) sd).getLanguageSettingProviders();
				((ILanguageSettingsProvidersKeeper) dd).setLanguageSettingProviders(newProviders);
			}
		}

		performOK();
	}

	@Override
	protected void performOK() {
		if (!page.isForPrefs()) {
			// FIXME: for now only handles current configuration
			ICResourceDescription rcDesc = getResDesc();
			IResource rc = getResource();
			ICConfigurationDescription cfgDescription = rcDesc.getConfiguration();
			if (cfgDescription instanceof ILanguageSettingsProvidersKeeper) {
				List<ILanguageSettingsProvider> destProviders = new ArrayList<ILanguageSettingsProvider>();
				List<ILanguageSettingsProvider> providers = ((ILanguageSettingsProvidersKeeper) cfgDescription).getLanguageSettingProviders();
				for (ILanguageSettingsProvider pro : providers) {
					// TODO: clone
					destProviders.add(pro);
				}
				((ILanguageSettingsProvidersKeeper) cfgDescription).setLanguageSettingProviders(destProviders);
			}
		}

		// Build Settings page
		if (page.isForPrefs()) {
			try {
				LanguageSettingsManager.setWorkspaceProviders(presentedProviders);
			} catch (CoreException e) {
				CUIPlugin.log("Error setting user defined providers", e);
			}
			initializeProviders();
		}

		if (page.isForProject() && enableProvidersCheckBox!=null) {
			boolean enabled = enableProvidersCheckBox.getSelection();
			if (masterPropertyPage!=null)
				enabled = masterPropertyPage.isLanguageSettingsProvidersEnabled();
			ScannerDiscoveryLegacySupport.setLanguageSettingsProvidersFunctionalityEnabled(page.getProject(), enabled);
			enableProvidersCheckBox.setSelection(enabled);
		}

		Collection<ICOptionPage> optionPages = optionsPageMap.values();
		for (ICOptionPage op : optionPages) {
			try {
				op.performApply(null);
			} catch (CoreException e) {
				CUIPlugin.log("Error applying options page", e);
			}
		}

		trackInitialSettings();
		updateData(getResDesc());
	}

	@Override
	public boolean canBeVisible() {
		if (CDTPrefUtil.getBool(CDTPrefUtil.KEY_NO_SHOW_PROVIDERS))
			return false;

		return page.isForPrefs() || page.isForProject();
	}

	private ILanguageSettingsProvider findRawProvider(String id, List<ILanguageSettingsProvider> providers) {
		for (ILanguageSettingsProvider provider : providers) {
			if (provider.getId().equals(id)) {
				provider = LanguageSettingsManager.getRawProvider(provider);
				return provider;
			}
		}
		return null;
	}

	private ILanguageSettingsProvider findProvider(String id, List<ILanguageSettingsProvider> providers) {
		for (ILanguageSettingsProvider provider : providers) {
			if (provider.getId().equals(id)) {
				return provider;
			}
		}
		return null;
	}

	public ILanguageSettingsProvider getProvider(String id) {
		return findProvider(id, presentedProviders);
	}

	private int getProviderIndex(String id, List<ILanguageSettingsProvider> providers) {
		int pos = 0;
		for (ILanguageSettingsProvider p : providers) {
			if (p.getId().equals(id))
				return pos;
			pos++;
		}
		return -1;
	}

//	private void informOptionPages(boolean apply) {
//	Collection<ICOptionPage> pages = optionsPageMap.values();
//	for (ICOptionPage dynamicPage : pages) {
//		if (dynamicPage!=null && dynamicPage.isValid() && dynamicPage.getControl() != null) {
//			try {
//				if (apply)
//					dynamicPage.performApply(new NullProgressMonitor());
//				else
//					dynamicPage.performDefaults();
//			} catch (CoreException e) {
//				CUIPlugin.log("ErrorParsTab.error.OnApplyingSettings", e);
//			}
//		}
//	}
//}

}
