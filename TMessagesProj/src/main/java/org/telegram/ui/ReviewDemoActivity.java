package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.glass.GlassTabView;

/** Local sample data. Never activates a UserConfig or creates a Telegram session. */
public class ReviewDemoActivity extends BaseFragment {
    public static final String PHONE = "12025550100";
    private static final int[] NAMES = {R.string.ReviewDemoContactOne, R.string.ReviewDemoContactTwo, R.string.ReviewDemoContactThree};
    private static final int[] MESSAGES = {R.string.ReviewDemoMessageOne, R.string.ReviewDemoMessageTwo, R.string.ReviewDemoMessageThree};
    private LinearLayout content;
    private MainTabsLayout navigation;
    private final GlassTabView[] tabs = new GlassTabView[4];

    @Override
    public View createView(Context context) {
        actionBar.setTitle(getString(R.string.ReviewDemoTitle));
        actionBar.setSubtitle(getString(R.string.ReviewDemoOffline));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout root = new FrameLayout(context);
        fragmentView = root;
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        ScrollView scroll = new ScrollView(context);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT,
                Gravity.TOP, 0, 0, 0, DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS));

        navigation = new MainTabsLayout(context, getResourceProvider());
        navigation.setNavigationDock();
        navigation.setPadding(dp(DialogsActivity.MAIN_TABS_MARGIN + 4), dp(DialogsActivity.MAIN_TABS_MARGIN + 4),
                dp(DialogsActivity.MAIN_TABS_MARGIN + 4), dp(DialogsActivity.MAIN_TABS_MARGIN + 4));
        navigation.setMaxWidth(dp(380));
        BlurredBackgroundSourceColor source = new BlurredBackgroundSourceColor();
        source.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        BlurredBackgroundDrawableViewFactory factory = new BlurredBackgroundDrawableViewFactory(source);
        BlurredBackgroundDrawable background = factory.create(navigation, BlurredBackgroundProviderImpl.mainTabs(getResourceProvider()));
        background.setPadding(dp(DialogsActivity.MAIN_TABS_MARGIN));
        background.setRadius(dp(DialogsActivity.MAIN_TABS_HEIGHT / 2f));
        navigation.setBackground(background);
        navigation.setNavigationLens(factory.create(navigation, BlurredBackgroundProviderImpl.mainTabs(getResourceProvider())));
        GlassTabView.TabAnimation[] icons = {GlassTabView.TabAnimation.CHATS, GlassTabView.TabAnimation.CONTACTS,
                GlassTabView.TabAnimation.SETTINGS, GlassTabView.TabAnimation.CONTACTS};
        int[] titles = {R.string.MainTabsChats, R.string.MainTabsContacts, R.string.Settings, R.string.MainTabsProfile};
        for (int i = 0; i < tabs.length; i++) {
            final int position = i;
            tabs[i] = GlassTabView.createMainTab(context, getResourceProvider(), icons[i], titles[i]);
            tabs[i].setNavigationDockStyle();
            tabs[i].setOnClickListener(v -> showPage(position));
            navigation.addView(tabs[i]);
            navigation.setViewVisible(tabs[i], true, false);
        }
        root.addView(navigation, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 11, 0, 11, 0));
        showPage(0);
        return fragmentView;
    }

    private void showPage(int position) {
        navigation.setSelectedPosition(position, content.getChildCount() > 0);
        navigation.setTabSelected(tabs[position], true);
        content.removeAllViews();
        Context context = content.getContext();
        TextInfoPrivacyCell notice = new TextInfoPrivacyCell(context);
        notice.setText(getString(R.string.ReviewDemoInfo));
        content.addView(notice);
        if (position == 0) {
            for (int i = 0; i < NAMES.length; i++) {
                final int index = i;
                DialogCell.CustomDialog dialog = new DialogCell.CustomDialog();
                dialog.id = i;
                dialog.name = getString(NAMES[i]);
                dialog.message = getString(MESSAGES[i]);
                dialog.date = (int) (System.currentTimeMillis() / 1000) - i * 3600;
                dialog.unread_count = i == 0 ? 2 : 0;
                dialog.pinned = i == 0;
                dialog.sent = DialogCell.SENT_STATE_NOTHING;
                DialogCell cell = new DialogCell(null, context, false, false, currentAccount, getResourceProvider());
                cell.setDialog(dialog);
                cell.useSeparator = i < NAMES.length - 1;
                cell.setOnClickListener(v -> presentFragment(new ReviewDemoChatActivity(getString(NAMES[index]), getString(MESSAGES[index]))));
                content.addView(cell);
            }
        } else if (position == 1) {
            for (int i = 0; i < NAMES.length; i++) {
                final int index = i;
                TextSettingsCell cell = new TextSettingsCell(context);
                cell.setText(getString(NAMES[i]), true);
                cell.setOnClickListener(v -> presentFragment(new ReviewDemoChatActivity(getString(NAMES[index]), getString(MESSAGES[index]))));
                content.addView(cell);
            }
        } else if (position == 2) {
            int[] labels = {R.string.FlexSettings, R.string.FlexLlmSettings, R.string.FlexTranslationSettings, R.string.FlexAdBlockSettings};
            for (int i = 0; i < labels.length; i++) {
                final int index = i;
                TextSettingsCell cell = new TextSettingsCell(context);
                cell.setText(getString(labels[i]), true);
                cell.setOnClickListener(v -> presentFragment(index == 0 ? new FlexSettingsActivity()
                        : index == 1 ? new FlexLlmSettingsActivity()
                        : index == 2 ? new FlexTranslateSettingsActivity() : new FlexAdBlockSettingsActivity()));
                content.addView(cell);
            }
        } else {
            TextInfoPrivacyCell profile = new TextInfoPrivacyCell(context);
            profile.setText(getString(R.string.ReviewDemoTitle) + "\n+1 202-555-0100\n" + getString(R.string.ReviewDemoOffline));
            content.addView(profile);
        }
    }
}
