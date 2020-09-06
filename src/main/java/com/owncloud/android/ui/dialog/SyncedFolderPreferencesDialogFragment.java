/*
 * Nextcloud Android client application
 *
 * @author Andy Scherzinger
 * Copyright (C) 2016 Andy Scherzinger
 * Copyright (C) 2016 Nextcloud
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU AFFERO GENERAL PUBLIC LICENSE
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU AFFERO GENERAL PUBLIC LICENSE for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.owncloud.android.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.owncloud.android.R;
import com.owncloud.android.datamodel.MediaFolderType;
import com.owncloud.android.datamodel.SyncedFolderDisplayItem;
import com.owncloud.android.lib.common.utils.Log_OC;
import com.owncloud.android.ui.activity.FolderPickerActivity;
import com.owncloud.android.ui.activity.UploadFilesActivity;
import com.owncloud.android.ui.dialog.parcel.SyncedFolderParcelable;
import com.owncloud.android.utils.DisplayUtils;
import com.owncloud.android.utils.ThemeUtils;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.DialogFragment;

import static com.owncloud.android.datamodel.SyncedFolderDisplayItem.UNPERSISTED_ID;
import static com.owncloud.android.ui.activity.UploadFilesActivity.REQUEST_CODE_KEY;

/**
 * Dialog to show the preferences/configuration of a synced folder allowing the user to change the different parameters.
 */
public class SyncedFolderPreferencesDialogFragment extends DialogFragment {

    public static final String SYNCED_FOLDER_PARCELABLE = "SyncedFolderParcelable";
    public static final int REQUEST_CODE__SELECT_REMOTE_FOLDER = 0;
    public static final int REQUEST_CODE__SELECT_LOCAL_FOLDER = 1;

    private final static String TAG = SyncedFolderPreferencesDialogFragment.class.getSimpleName();
    private static final String BEHAVIOUR_DIALOG_STATE = "BEHAVIOUR_DIALOG_STATE";
    private final static float alphaEnabled = 1.0f;
    private final static float alphaDisabled = 0.7f;

    protected View mView;
    private CharSequence[] mUploadBehaviorItemStrings;
    private SwitchCompat mEnabledSwitch;
    private AppCompatCheckBox mUploadOnWifiCheckbox;
    private AppCompatCheckBox mUploadOnChargingCheckbox;
    private AppCompatCheckBox mUploadExistingCheckbox;
    private AppCompatCheckBox mUploadUseSubfoldersCheckbox;
    private TextView mUploadBehaviorSummary;
    private TextView mLocalFolderPath;
    private TextView mLocalFolderSummary;
    private TextView mRemoteFolderSummary;

    private SyncedFolderParcelable mSyncedFolder;
    private MaterialButton mCancel;
    private MaterialButton mSave;
    private boolean behaviourDialogShown;
    private AlertDialog behaviourDialog;

    public static SyncedFolderPreferencesDialogFragment newInstance(SyncedFolderDisplayItem syncedFolder, int section) {
        if (syncedFolder == null) {
            throw new IllegalArgumentException("SyncedFolder is mandatory but NULL!");
        }

        Bundle args = new Bundle();
        args.putParcelable(SYNCED_FOLDER_PARCELABLE, new SyncedFolderParcelable(syncedFolder, section));

        SyncedFolderPreferencesDialogFragment dialogFragment = new SyncedFolderPreferencesDialogFragment();
        dialogFragment.setArguments(args);
        dialogFragment.setStyle(STYLE_NORMAL, R.style.Theme_ownCloud_Dialog);

        return dialogFragment;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (!(activity instanceof OnSyncedFolderPreferenceListener)) {
            throw new IllegalArgumentException("The host activity must implement "
                    + OnSyncedFolderPreferenceListener.class.getCanonicalName());
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // keep the state of the fragment on configuration changes
        setRetainInstance(true);

        mView = null;

        mSyncedFolder = getArguments().getParcelable(SYNCED_FOLDER_PARCELABLE);
        mUploadBehaviorItemStrings = getResources().getTextArray(R.array.pref_behaviour_entries);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log_OC.d(TAG, "onCreateView, savedInstanceState is " + savedInstanceState);

        mView = inflater.inflate(R.layout.synced_folders_settings_layout, container, false);

        setupDialogElements(mView);
        setupListeners(mView);

        return mView;
    }

    /**
     * find all relevant UI elements and set their values.
     *
     * @param view the parent view
     */
    private void setupDialogElements(View view) {
        int accentColor = ThemeUtils.primaryAccentColor(getContext());

        if (mSyncedFolder.getType().getId() > MediaFolderType.CUSTOM.getId()) {
            // hide local folder chooser and delete for non-custom folders
            view.findViewById(R.id.local_folder_container).setVisibility(View.GONE);
            view.findViewById(R.id.delete).setVisibility(View.GONE);
        } else if (mSyncedFolder.getId() <= UNPERSISTED_ID) {
            // Hide delete/enabled for unpersisted custom folders
            view.findViewById(R.id.delete).setVisibility(View.GONE);
            view.findViewById(R.id.sync_enabled).setVisibility(View.GONE);

            // auto set custom folder to enabled
            mSyncedFolder.setEnabled(true);

            // switch text to create headline
            ((TextView) view.findViewById(R.id.synced_folders_settings_title))
                    .setText(R.string.autoupload_create_new_custom_folder);

            // disable save button
            view.findViewById(R.id.save).setEnabled(false);
        } else {
            view.findViewById(R.id.local_folder_container).setVisibility(View.GONE);
        }

        // find/saves UI elements
        mEnabledSwitch = view.findViewById(R.id.sync_enabled);
        ThemeUtils.tintSwitch(mEnabledSwitch, accentColor);

        mLocalFolderPath = view.findViewById(R.id.synced_folders_settings_local_folder_path);

        mLocalFolderSummary = view.findViewById(R.id.local_folder_summary);
        mRemoteFolderSummary = view.findViewById(R.id.remote_folder_summary);

        mUploadOnWifiCheckbox = view.findViewById(R.id.setting_instant_upload_on_wifi_checkbox);
        ThemeUtils.tintCheckbox(mUploadOnWifiCheckbox, accentColor);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            view.findViewById(R.id.setting_instant_upload_on_charging_container).setVisibility(View.GONE);
        } else {
            view.findViewById(R.id.setting_instant_upload_on_charging_container).setVisibility(View.VISIBLE);

            mUploadOnChargingCheckbox = view.findViewById(
                    R.id.setting_instant_upload_on_charging_checkbox);
            ThemeUtils.tintCheckbox(mUploadOnChargingCheckbox, accentColor);
        }

        mUploadExistingCheckbox = view.findViewById(R.id.setting_instant_upload_existing_checkbox);
        ThemeUtils.tintCheckbox(mUploadExistingCheckbox, accentColor);

        mUploadUseSubfoldersCheckbox = view.findViewById(
                R.id.setting_instant_upload_path_use_subfolders_checkbox);
        ThemeUtils.tintCheckbox(mUploadUseSubfoldersCheckbox, accentColor);

        mUploadBehaviorSummary = view.findViewById(R.id.setting_instant_behaviour_summary);

        mCancel = view.findViewById(R.id.cancel);
        ThemeUtils.themeDialogActionButton(mCancel);

        mSave = view.findViewById(R.id.save);
        ThemeUtils.themeDialogActionButton(mSave);

        // Set values
        setEnabled(mSyncedFolder.isEnabled());

        if (!TextUtils.isEmpty(mSyncedFolder.getLocalPath())) {
            mLocalFolderPath.setText(
                    DisplayUtils.createTextWithSpan(
                            String.format(
                                    getString(R.string.synced_folders_preferences_folder_path),
                                    mSyncedFolder.getLocalPath()),
                            mSyncedFolder.getFolderName(),
                            new StyleSpan(Typeface.BOLD)));
            mLocalFolderSummary.setText(mSyncedFolder.getLocalPath());
        } else {
            mLocalFolderSummary.setText(R.string.choose_local_folder);
        }

        if (!TextUtils.isEmpty(mSyncedFolder.getLocalPath())) {
            mRemoteFolderSummary.setText(mSyncedFolder.getRemotePath());
        } else {
            mRemoteFolderSummary.setText(R.string.choose_remote_folder);
        }

        mUploadOnWifiCheckbox.setChecked(mSyncedFolder.isWifiOnly());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            mUploadOnChargingCheckbox.setChecked(mSyncedFolder.isChargingOnly());
        }
        mUploadExistingCheckbox.setChecked(mSyncedFolder.isExisting());
        mUploadUseSubfoldersCheckbox.setChecked(mSyncedFolder.isSubfolderByDate());

        mUploadBehaviorSummary.setText(mUploadBehaviorItemStrings[mSyncedFolder.getUploadActionInteger()]);
    }

    /**
     * set correct icon/flag.
     *
     * @param enabled if enabled or disabled
     */
    private void setEnabled(boolean enabled) {
        mSyncedFolder.setEnabled(enabled);
        mEnabledSwitch.setChecked(enabled);

        setupViews(mView, enabled);
    }

    /**
     * set (new) remote path on activity result of the folder picker activity. The result gets originally propagated
     * to the underlying activity since the picker is an activity and the result can't get passed to the dialog
     * fragment directly.
     *
     * @param path the remote path to be set
     */
    public void setRemoteFolderSummary(String path) {
        mSyncedFolder.setRemotePath(path);
        mRemoteFolderSummary.setText(path);
        checkAndUpdateSaveButtonState();
    }

    /**
     * set (new) local path on activity result of the folder picker activity. The result gets originally propagated
     * to the underlying activity since the picker is an activity and the result can't get passed to the dialog
     * fragment directly.
     *
     * @param path the remote path to be set
     */
    public void setLocalFolderSummary(String path) {
        mSyncedFolder.setLocalPath(path);
        mLocalFolderSummary.setText(path);
        mLocalFolderPath.setText(
                DisplayUtils.createTextWithSpan(
                        String.format(
                                getString(R.string.synced_folders_preferences_folder_path),
                                mSyncedFolder.getLocalPath()),
                        new File(mSyncedFolder.getLocalPath()).getName(),
                        new StyleSpan(Typeface.BOLD)));
        checkAndUpdateSaveButtonState();
    }

    private void checkAndUpdateSaveButtonState() {
        if (mSyncedFolder.getLocalPath() != null && mSyncedFolder.getRemotePath() != null) {
            mView.findViewById(R.id.save).setEnabled(true);
        } else {
            mView.findViewById(R.id.save).setEnabled(false);
        }

        checkWritableFolder();
    }

    private void checkWritableFolder() {
        if (mSyncedFolder.getLocalPath() != null && new File(mSyncedFolder.getLocalPath()).canWrite()) {
            mView.findViewById(R.id.setting_instant_behaviour_container).setEnabled(true);
            mView.findViewById(R.id.setting_instant_behaviour_container).setAlpha(alphaEnabled);
            mUploadBehaviorSummary.setText(mUploadBehaviorItemStrings[mSyncedFolder.getUploadActionInteger()]);
        } else {
            mView.findViewById(R.id.setting_instant_behaviour_container).setEnabled(false);
            mView.findViewById(R.id.setting_instant_behaviour_container).setAlpha(alphaDisabled);

            mSyncedFolder.setUploadAction(
                getResources().getTextArray(R.array.pref_behaviour_entryValues)[0].toString());

            mUploadBehaviorSummary.setText(R.string.auto_upload_file_behaviour_kept_in_folder);
        }
    }

    private void setupViews(View view, boolean enable) {
        float alpha;
        if (enable) {
            alpha = alphaEnabled;
        } else {
            alpha = alphaDisabled;
        }
        view.findViewById(R.id.setting_instant_upload_on_wifi_container).setEnabled(enable);
        view.findViewById(R.id.setting_instant_upload_on_wifi_container).setAlpha(alpha);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            view.findViewById(R.id.setting_instant_upload_on_charging_container).setEnabled(enable);
            view.findViewById(R.id.setting_instant_upload_on_charging_container).setAlpha(alpha);
        }

        view.findViewById(R.id.setting_instant_upload_existing_container).setEnabled(enable);
        view.findViewById(R.id.setting_instant_upload_existing_container).setAlpha(alpha);

        view.findViewById(R.id.setting_instant_upload_path_use_subfolders_container).setEnabled(enable);
        view.findViewById(R.id.setting_instant_upload_path_use_subfolders_container).setAlpha(alpha);

        view.findViewById(R.id.remote_folder_container).setEnabled(enable);
        view.findViewById(R.id.remote_folder_container).setAlpha(alpha);

        view.findViewById(R.id.local_folder_container).setEnabled(enable);
        view.findViewById(R.id.local_folder_container).setAlpha(alpha);

        checkWritableFolder();
    }

    /**
     * setup all listeners.
     *
     * @param view the parent view
     */
    private void setupListeners(View view) {
        mSave.setOnClickListener(new OnSyncedFolderSaveClickListener());
        mCancel.setOnClickListener(new OnSyncedFolderCancelClickListener());
        view.findViewById(R.id.delete).setOnClickListener(new OnSyncedFolderDeleteClickListener());

        view.findViewById(R.id.setting_instant_upload_on_wifi_container).setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mSyncedFolder.setWifiOnly(!mSyncedFolder.isWifiOnly());
                        mUploadOnWifiCheckbox.toggle();
                    }
                });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {

            view.findViewById(R.id.setting_instant_upload_on_charging_container).setOnClickListener(
                    new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mSyncedFolder.setChargingOnly(!mSyncedFolder.isChargingOnly());
                            mUploadOnChargingCheckbox.toggle();
                        }
                    });
        }

        view.findViewById(R.id.setting_instant_upload_existing_container).setOnClickListener(
              new OnClickListener() {
                  @Override
                  public void onClick(View v) {
                      mSyncedFolder.setExisting(!mSyncedFolder.isExisting());
                      mUploadExistingCheckbox.toggle();
                  }
              });

        view.findViewById(R.id.setting_instant_upload_path_use_subfolders_container).setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mSyncedFolder.setSubfolderByDate(!mSyncedFolder.isSubfolderByDate());
                        mUploadUseSubfoldersCheckbox.toggle();
                    }
                });

        view.findViewById(R.id.remote_folder_container).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent action = new Intent(getActivity(), FolderPickerActivity.class);
                getActivity().startActivityForResult(action, REQUEST_CODE__SELECT_REMOTE_FOLDER);
            }
        });

        mRemoteFolderSummary.setOnClickListener(textView -> {
            mRemoteFolderSummary.setEllipsize(null);
            mRemoteFolderSummary.setMaxLines(Integer.MAX_VALUE);
        });

        view.findViewById(R.id.local_folder_container).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent action = new Intent(getActivity(), UploadFilesActivity.class);
                action.putExtra(UploadFilesActivity.KEY_LOCAL_FOLDER_PICKER_MODE, true);
                action.putExtra(REQUEST_CODE_KEY, REQUEST_CODE__SELECT_LOCAL_FOLDER);
                getActivity().startActivityForResult(action, REQUEST_CODE__SELECT_LOCAL_FOLDER);
            }
        });

        mLocalFolderSummary.setOnClickListener(textView -> {
            mLocalFolderSummary.setEllipsize(null);
            mLocalFolderSummary.setMaxLines(Integer.MAX_VALUE);
        });

        view.findViewById(R.id.sync_enabled).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setEnabled(!mSyncedFolder.isEnabled());
            }
        });

        view.findViewById(R.id.setting_instant_behaviour_container).setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showBehaviourDialog();
                    }
                });
    }

    private void showBehaviourDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(ThemeUtils.getColoredTitle(
                getResources().getString(R.string.prefs_instant_behaviour_dialogTitle),
                ThemeUtils.primaryAccentColor(getContext())))
                .setSingleChoiceItems(getResources().getTextArray(R.array.pref_behaviour_entries),
                        mSyncedFolder.getUploadActionInteger(),
                        new
                                DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        mSyncedFolder.setUploadAction(
                                                getResources().getTextArray(
                                                        R.array.pref_behaviour_entryValues)[which].toString());
                                        mUploadBehaviorSummary.setText(SyncedFolderPreferencesDialogFragment
                                                .this.mUploadBehaviorItemStrings[which]);
                                        behaviourDialogShown = false;
                                        dialog.dismiss();
                                    }
                                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        behaviourDialogShown = false;
                    }
                });
        behaviourDialogShown = true;
        behaviourDialog = builder.create();
        behaviourDialog.show();
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setTitle(null);
        return dialog;
    }

    @Override
    public void onDestroyView() {
        Log_OC.d(TAG, "destroy SyncedFolderPreferencesDialogFragment view");
        if (getDialog() != null && getRetainInstance()) {
            getDialog().setDismissMessage(null);
        }

        if (behaviourDialog != null && behaviourDialog.isShowing()) {
            behaviourDialog.dismiss();
        }

        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(BEHAVIOUR_DIALOG_STATE, behaviourDialogShown);

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        behaviourDialogShown = savedInstanceState != null &&
                savedInstanceState.getBoolean(BEHAVIOUR_DIALOG_STATE, false);

        if (behaviourDialogShown) {
            showBehaviourDialog();
        }

        super.onViewStateRestored(savedInstanceState);
    }

    public interface OnSyncedFolderPreferenceListener {
        void onSaveSyncedFolderPreference(SyncedFolderParcelable syncedFolder);

        void onCancelSyncedFolderPreference();

        void onDeleteSyncedFolderPreference(SyncedFolderParcelable syncedFolder);
    }

    private class OnSyncedFolderSaveClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            dismiss();
            ((OnSyncedFolderPreferenceListener) getActivity()).onSaveSyncedFolderPreference(mSyncedFolder);
        }
    }

    private class OnSyncedFolderCancelClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            dismiss();
            ((OnSyncedFolderPreferenceListener) getActivity()).onCancelSyncedFolderPreference();
        }
    }

    private class OnSyncedFolderDeleteClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            dismiss();
            ((OnSyncedFolderPreferenceListener) getActivity()).onDeleteSyncedFolderPreference(mSyncedFolder);
        }
    }
}