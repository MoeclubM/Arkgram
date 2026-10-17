package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FlexConfig;
import org.telegram.messenger.R;
import org.telegram.messenger.TranslateController;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TranslateAlert2;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class FlexTranslateSettingsActivity extends UniversalFragment {

    private static final int ID_SHOW_BUTTON = 1;
    private static final int ID_SHOW_CHAT_BUTTON = 2;
    private static final int ID_PROVIDER = 3;
    private static final int ID_TARGET_LANGUAGE = 4;
    private static final int ID_DEEPL_API_URL = 5;
    private static final int ID_DEEPL_API_KEY = 6;
    private static final int ID_LLM_SETTINGS = 7;
    @Override
    protected CharSequence getTitle() {
        return getString(R.string.FlexTranslationSettings);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        TranslateController translateController = getMessagesController().getTranslateController();
        String toLanguage = TranslateAlert2.languageName(TranslateAlert2.getToLanguage());
        if (toLanguage == null) {
            toLanguage = TranslateAlert2.getToLanguage();
        }

        items.add(UItem.asHeader(getString(R.string.TranslateMessages)));
        items.add(UItem.asCheck(ID_SHOW_BUTTON, getString(R.string.ShowTranslateButton)).setChecked(translateController.isContextTranslateEnabled()));
        items.add(UItem.asCheck(ID_SHOW_CHAT_BUTTON, getString(R.string.ShowTranslateChatButton)).setChecked(translateController.isChatTranslateEnabled()));
        items.add(UItem.asButton(ID_PROVIDER, R.drawable.msg_translate, getString(R.string.FlexTranslationProvider), getProviderTitle()));
        items.add(UItem.asButton(ID_TARGET_LANGUAGE, R.drawable.msg2_language, getString(R.string.FlexTranslationTargetLanguage), TranslateAlert2.capitalFirst(toLanguage)));
        if (FlexConfig.getTranslationProvider() == FlexConfig.TRANSLATION_PROVIDER_DEEPL) {
            items.add(UItem.asHeader(getString(R.string.FlexTranslationProviderConfig)));
            items.add(UItem.asButton(ID_DEEPL_API_URL, R.drawable.msg2_data, getString(R.string.FlexTranslationDeepLApiUrl), FlexConfig.getDeepLApiUrl()));
            items.add(UItem.asButton(ID_DEEPL_API_KEY, R.drawable.msg_translate, getString(R.string.FlexTranslationDeepLApiKey), formatSecretValue(FlexConfig.getDeepLApiKey())));
            items.add(UItem.asShadow(getString(R.string.FlexTranslationDeepLApiUrlHint)));
        } else if (FlexConfig.getTranslationProvider() == FlexConfig.TRANSLATION_PROVIDER_LLM) {
            items.add(UItem.asHeader(getString(R.string.FlexTranslationProviderConfig)));
            items.add(UItem.asButton(ID_LLM_SETTINGS, R.drawable.outline_ai_translate2, getString(R.string.FlexTranslationLlmSettings), getLlmSettingsValue()));
            items.add(UItem.asShadow(getString(R.string.FlexTranslationLlmSettingsInfo)));
        }
        items.add(UItem.asShadow(getString(R.string.FlexTranslationInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        TranslateController translateController = getMessagesController().getTranslateController();
        if (item.id == ID_SHOW_BUTTON) {
            translateController.setContextTranslateEnabled(!translateController.isContextTranslateEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_SHOW_CHAT_BUTTON) {
            translateController.setChatTranslateEnabled(!translateController.isChatTranslateEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_PROVIDER) {
            showProviderDialog();
        } else if (item.id == ID_TARGET_LANGUAGE) {
            showLanguageDialog();
        } else if (item.id == ID_DEEPL_API_URL) {
            showTextValueDialog(getString(R.string.FlexTranslationDeepLApiUrl), getString(R.string.FlexTranslationDeepLApiUrlHint), FlexConfig.getDeepLApiUrl(), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, value -> {
                FlexConfig.setDeepLApiUrl(value);
                listView.adapter.update(true);
            });
        } else if (item.id == ID_DEEPL_API_KEY) {
            showTextValueDialog(getString(R.string.FlexTranslationDeepLApiKey), null, FlexConfig.getDeepLApiKey(), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, value -> {
                FlexConfig.setDeepLApiKey(value);
                listView.adapter.update(true);
            });
        } else if (item.id == ID_LLM_SETTINGS) {
            presentFragment(new FlexLlmFeatureSettingsActivity(false));
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void showProviderDialog() {
        CharSequence[] items = new CharSequence[] {
            getString(R.string.FlexTranslationProviderTelegram),
            getString(R.string.FlexTranslationProviderGoogle),
            getString(R.string.FlexTranslationProviderGoogleCn),
            getString(R.string.FlexTranslationProviderDeepL),
            getString(R.string.FlexTranslationProviderLlm)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getString(R.string.FlexTranslationProvider));
        builder.setItems(items, (dialog, which) -> {
            FlexConfig.setTranslationProvider(which);
            listView.adapter.update(true);
            if (which == FlexConfig.TRANSLATION_PROVIDER_LLM) presentFragment(new FlexLlmFeatureSettingsActivity(false));
        });
        showDialog(builder.create());
    }

    private void showLanguageDialog() {
        ArrayList<TranslateController.Language> languages = TranslateController.getLanguages();
        CharSequence[] items = new CharSequence[languages.size()];
        for (int i = 0; i < languages.size(); i++) {
            items[i] = languages.get(i).displayName;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getString(R.string.FlexTranslationTargetLanguage));
        builder.setItems(items, (dialog, which) -> {
            TranslateAlert2.setToLanguage(languages.get(which).code);
            listView.adapter.update(true);
        });
        showDialog(builder.create());
    }

    private CharSequence getProviderTitle() {
        int provider = FlexConfig.getTranslationProvider();
        if (provider == FlexConfig.TRANSLATION_PROVIDER_GOOGLE_CN) {
            return getString(R.string.FlexTranslationProviderGoogleCn);
        }
        if (provider == FlexConfig.TRANSLATION_PROVIDER_GOOGLE) {
            return getString(R.string.FlexTranslationProviderGoogle);
        }
        if (provider == FlexConfig.TRANSLATION_PROVIDER_DEEPL) {
            return getString(R.string.FlexTranslationProviderDeepL);
        }
        if (provider == FlexConfig.TRANSLATION_PROVIDER_LLM) {
            return getString(R.string.FlexTranslationProviderLlm);
        }
        return getString(R.string.FlexTranslationProviderTelegram);
    }

    private CharSequence formatSecretValue(String value) {
        if (TextUtils.isEmpty(value)) {
            return getString(R.string.FlexTranslationNotSet);
        }
        if (value.length() <= 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }

    private CharSequence getLlmSettingsValue() {
        String model = FlexConfig.getLlmModel();
        CharSequence provider = FlexLlmFeatureSettingsActivity.getProviderApiTitle(FlexConfig.getTranslationLlmProvider());
        return TextUtils.isEmpty(model) ? provider : provider + " / " + model;
    }

    private void showTextValueDialog(String title, String hint, String value, int inputType, Utilities.Callback<String> onSave) {
        if (getContext() == null) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(title);

        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);

        if (!TextUtils.isEmpty(hint)) {
            TextView hintView = new TextView(getContext());
            hintView.setText(hint);
            hintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            hintView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourceProvider));
            container.addView(hintView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 4, 24, 12));
        }

        EditTextBoldCursor editText = new EditTextBoldCursor(getContext());
        editText.setInputType(inputType);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setText(value);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        editText.setHintColor(Theme.getColor(Theme.key_groupcreate_hintText, resourceProvider));
        editText.setCursorColor(Theme.getColor(Theme.key_chat_messagePanelCursor, resourceProvider));
        editText.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourceProvider), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourceProvider), Theme.getColor(Theme.key_text_RedRegular, resourceProvider));
        editText.setSingleLine(true);
        editText.setFocusable(true);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setBackgroundDrawable(null);
        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 12));

        builder.setView(container);
        builder.setWidth(AndroidUtilities.dp(292));
        builder.setPositiveButton(getString(R.string.Done), (dialog, which) -> onSave.run(editText.getText().toString().trim()));
        builder.setNegativeButton(getString(R.string.Cancel), null);
        if (!TextUtils.isEmpty(value)) {
            builder.setNeutralButton(getString(R.string.FlexTranslationClearValue), (dialog, which) -> onSave.run(""));
        }

        AlertDialog dialog = builder.create();
        showDialog(dialog);
        editText.setSelection(editText.getText().length());
    }
}
