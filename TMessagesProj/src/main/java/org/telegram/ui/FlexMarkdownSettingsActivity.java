package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import org.telegram.messenger.FlexConfig;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class FlexMarkdownSettingsActivity extends UniversalFragment {

    private static final int ID_DISABLE_MARKDOWN = 1;
    private static final int ID_PARSE_LINKS = 3;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.FlexMarkdownSettings);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.FlexMarkdownSettings)));
        items.add(UItem.asCheck(ID_DISABLE_MARKDOWN, getString(R.string.FlexDisableMarkdown)).setChecked(FlexConfig.isMarkdownDisabled()));
        items.add(UItem.asCheck(ID_PARSE_LINKS, getString(R.string.FlexMarkdownParseLinks)).setChecked(FlexConfig.isMarkdownParseLinksEnabled()));
        items.add(UItem.asShadow(getString(R.string.FlexMarkdownInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_DISABLE_MARKDOWN) {
            FlexConfig.setMarkdownDisabled(!FlexConfig.isMarkdownDisabled());
            listView.adapter.update(true);
        } else if (item.id == ID_PARSE_LINKS) {
            FlexConfig.setMarkdownParseLinksEnabled(!FlexConfig.isMarkdownParseLinksEnabled());
            listView.adapter.update(true);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
