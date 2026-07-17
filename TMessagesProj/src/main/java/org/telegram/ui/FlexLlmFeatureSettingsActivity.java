package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FlexConfig;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class FlexLlmFeatureSettingsActivity extends UniversalFragment {

    private static final int ID_MODEL = 1;
    private static final int ID_PROMPT = 2;

    private final boolean summary;

    public FlexLlmFeatureSettingsActivity(boolean summary) {
        this.summary = summary;
    }

    @Override
    protected CharSequence getTitle() {
        return summary ? getString(R.string.FlexAiSummarySettings) : getString(R.string.FlexTranslationLlmSettings);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getTitle()));
        items.add(UItem.asButton(ID_MODEL, R.drawable.msg2_data, getString(R.string.FlexLlmModel), formatPlainValue(getModel())));
        items.add(UItem.asButton(ID_PROMPT, R.drawable.menu_feature_code, getString(R.string.FlexLlmPrompt), formatPromptValue(getPrompt())));
        items.add(UItem.asShadow(getInfoText()));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_MODEL) {
            showModelDialog();
        } else if (item.id == ID_PROMPT) {
            showTextValueDialog(getString(R.string.FlexLlmPrompt), getString(R.string.FlexLlmPromptHint), getPrompt(), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, true, this::setPrompt);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    public static CharSequence getProviderTitle(int provider) {
        if (provider == FlexConfig.LLM_PROVIDER_OPENAI) {
            return getString(R.string.FlexLlmProviderOpenAi);
        }
        if (provider == FlexConfig.LLM_PROVIDER_OPENROUTER) {
            return getString(R.string.FlexLlmProviderOpenRouter);
        }
        if (provider == FlexConfig.LLM_PROVIDER_DEEPSEEK) {
            return getString(R.string.FlexLlmProviderDeepSeek);
        }
        if (provider == FlexConfig.LLM_PROVIDER_GROQ) {
            return getString(R.string.FlexLlmProviderGroq);
        }
        if (provider == FlexConfig.LLM_PROVIDER_SILICONFLOW) {
            return getString(R.string.FlexLlmProviderSiliconFlow);
        }
        if (provider == FlexConfig.LLM_PROVIDER_ANTHROPIC) {
            return getString(R.string.FlexLlmProviderAnthropic);
        }
        return getString(R.string.FlexLlmProviderCustom);
    }

    public static CharSequence getApiTypeTitle(int apiType) {
        if (apiType == FlexConfig.LLM_API_TYPE_RESPONSES) {
            return getString(R.string.FlexLlmApiTypeResponses);
        }
        if (apiType == FlexConfig.LLM_API_TYPE_CLAUDE_MESSAGES) {
            return getString(R.string.FlexLlmApiTypeClaudeMessages);
        }
        return getString(R.string.FlexLlmApiTypeChatCompletions);
    }

    public static CharSequence getProviderApiTitle(int provider) {
        return getProviderTitle(provider) + " [" + getApiTypeTitle(FlexConfig.getProviderApiType(provider)) + "]";
    }

    private void setModelRef(String value) {
        if (summary) {
            FlexConfig.setAiSummaryLlmModelRef(value);
        } else {
            FlexConfig.setTranslationLlmModelRef(value);
        }
    }

    private String getModel() {
        return summary ? FlexConfig.getAiSummaryLlmModel() : FlexConfig.getLlmModel();
    }

    private String getPrompt() {
        return summary ? FlexConfig.getAiSummaryLlmPrompt() : FlexConfig.getLlmPrompt();
    }

    private void setPrompt(String value) {
        if (summary) {
            FlexConfig.setAiSummaryLlmPrompt(value);
        } else {
            FlexConfig.setLlmPrompt(value);
        }
    }

    private String getInfoText() {
        return summary ? getString(R.string.FlexAiSummarySettingsInfo) : getString(R.string.FlexTranslationLlmSettingsInfo);
    }

    private CharSequence formatPlainValue(String value) {
        if (TextUtils.isEmpty(value)) {
            return getString(R.string.FlexLlmNotSet);
        }
        int provider = summary ? FlexConfig.getAiSummaryLlmProvider() : FlexConfig.getTranslationLlmProvider();
        return getProviderApiTitle(provider) + " / " + value;
    }

    private CharSequence formatPromptValue(String value) {
        if (TextUtils.isEmpty(value)) {
            return getString(R.string.FlexLlmNotSet);
        }
        String singleLine = value.replace('\n', ' ').trim();
        if (singleLine.length() <= 36) {
            return singleLine;
        }
        return singleLine.substring(0, 36) + "...";
    }

    private void showModelDialog() {
        ArrayList<String> refs = new ArrayList<>();
        ArrayList<CharSequence> items = new ArrayList<>();
        for (int provider = FlexConfig.LLM_PROVIDER_CUSTOM; provider <= FlexConfig.LLM_PROVIDER_ANTHROPIC; ++provider) {
            ArrayList<String> models = FlexConfig.getProviderModelList(provider);
            for (int i = 0; i < models.size(); ++i) {
                String model = models.get(i);
                refs.add(FlexConfig.makeLlmModelRef(provider, model));
                items.add(getProviderApiTitle(provider) + " / " + model);
            }
        }
        if (items.isEmpty()) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.FlexLlmNoModelsConfigured)).show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getString(R.string.FlexLlmModel));
        builder.setItems(items.toArray(new CharSequence[0]), (dialog, which) -> {
            setModelRef(refs.get(which));
            listView.adapter.update(true);
        });
        showDialog(builder.create());
    }

    private void showTextValueDialog(String title, String hint, String value, int inputType, boolean multiline, Utilities.Callback<String> onSave) {
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
        editText.setSingleLine(!multiline);
        editText.setMaxLines(multiline ? 12 : 1);
        if (multiline) {
            editText.setMinLines(6);
            editText.setGravity(Gravity.TOP | (org.telegram.messenger.LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
        }
        editText.setFocusable(true);
        editText.setImeOptions(multiline ? EditorInfo.IME_FLAG_NO_ENTER_ACTION : EditorInfo.IME_ACTION_DONE);
        editText.setBackgroundDrawable(null);
        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 12));

        builder.setView(container);
        builder.setWidth(AndroidUtilities.dp(multiline ? 320 : 292));
        builder.setPositiveButton(getString(R.string.Done), (dialog, which) -> onSave.run(editText.getText().toString().trim()));
        builder.setNegativeButton(getString(R.string.Cancel), null);
        if (!TextUtils.isEmpty(value)) {
            builder.setNeutralButton(getString(R.string.FlexLlmClearValue), (dialog, which) -> onSave.run(""));
        }

        AlertDialog dialog = builder.create();
        showDialog(dialog);
        editText.setSelection(editText.getText().length());
    }
}
