package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.FlexConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class FlexLlmSettingsActivity extends UniversalFragment {

    private static final int ID_TRANSLATION = 1;
    private static final int ID_AI_SUMMARY = 2;
    private static final int ID_OFFICIAL_AI = 3;
    private static final int ID_PROVIDER_BASE = 100;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.FlexLlmSettings);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.FlexLlmProviders)));
        for (int provider = FlexConfig.LLM_PROVIDER_CUSTOM; provider <= FlexConfig.LLM_PROVIDER_ANTHROPIC; ++provider) {
            items.add(UItem.asButton(ID_PROVIDER_BASE + provider, R.drawable.msg2_data, FlexLlmFeatureSettingsActivity.getProviderApiTitle(provider), getProviderValue(provider)));
        }
        items.add(UItem.asShadow(getString(R.string.FlexLlmSettingsInfo)));
        items.add(UItem.asHeader(getString(R.string.FlexLlmFeatures)));
        items.add(UItem.asButton(ID_TRANSLATION, R.drawable.msg_translate, getString(R.string.FlexTranslationLlmSettings), getFeatureValue(false)));
        items.add(UItem.asButton(ID_AI_SUMMARY, R.drawable.outline_ai_translate2, getString(R.string.FlexAiSummarySettings), getFeatureValue(true)));
        String ref = FlexConfig.getOfficialAiModelRef();
        items.add(UItem.asButton(ID_OFFICIAL_AI, R.drawable.outline_ai_translate2, getString(R.string.FlexOfficialAi), ref.isEmpty() ? getString(R.string.FlexTranslationProviderTelegram) : FlexLlmFeatureSettingsActivity.getProviderApiTitle(FlexConfig.getLlmProviderFromRef(ref)) + " / " + FlexConfig.getLlmModelFromRef(ref)));
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_TRANSLATION) {
            presentFragment(new FlexLlmFeatureSettingsActivity(false));
        } else if (item.id == ID_AI_SUMMARY) {
            presentFragment(new FlexLlmFeatureSettingsActivity(true));
        } else if (item.id == ID_OFFICIAL_AI) {
            presentFragment(new FlexLlmFeatureSettingsActivity(FlexLlmFeatureSettingsActivity.OFFICIAL_AI));
        } else if (item.id >= ID_PROVIDER_BASE) {
            presentFragment(new FlexLlmProviderSettingsActivity(item.id - ID_PROVIDER_BASE));
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private CharSequence getProviderValue(int provider) {
        ArrayList<String> models = FlexConfig.getProviderModelList(provider);
        if (models.isEmpty()) {
            return getString(R.string.FlexLlmNotSet);
        }
        if (models.size() == 1) {
            return models.get(0);
        }
        return LocaleController.formatString(R.string.FlexLlmModelsCount, models.size());
    }

    private CharSequence getFeatureValue(boolean summary) {
        int provider = summary ? FlexConfig.getAiSummaryLlmProvider() : FlexConfig.getTranslationLlmProvider();
        String model = summary ? FlexConfig.getAiSummaryLlmModel() : FlexConfig.getLlmModel();
        if (TextUtils.isEmpty(model)) {
            return getString(R.string.FlexLlmNotSet);
        }
        return FlexLlmFeatureSettingsActivity.getProviderApiTitle(provider) + " / " + model;
    }
}
