package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

/** Messages exist only for this screen's lifetime; sending never calls Telegram. */
public class ReviewDemoChatActivity extends BaseFragment {
    private final String title, sample;
    private LinearLayout messages;
    private int messageId;

    public ReviewDemoChatActivity(String title, String sample) {
        this.title = title;
        this.sample = sample;
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(title);
        actionBar.setSubtitle(getString(R.string.ReviewDemoOffline));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = root;
        TextInfoPrivacyCell notice = new TextInfoPrivacyCell(context);
        notice.setText(getString(R.string.ReviewDemoChatInfo));
        root.addView(notice);
        ScrollView scroll = new ScrollView(context);
        messages = new LinearLayout(context);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(0, dp(8), 0, dp(8));
        scroll.addView(messages);
        root.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1));
        addMessage(sample, false);
        addMessage(getString(R.string.ReviewDemoReply), true);
        addMessage(getString(R.string.ReviewDemoMessageTwo), false);

        LinearLayout composer = new LinearLayout(context);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(12), dp(4), dp(8), dp(4));
        composer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        input.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        input.setHint(getString(R.string.ReviewDemoCompose));
        input.setTextSize(16);
        composer.addView(input, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1));
        Button send = new Button(context);
        send.setText(getString(R.string.Send));
        send.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) return;
            addMessage(text, true);
            input.setText("");
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        });
        composer.addView(send);
        root.addView(composer);
        return fragmentView;
    }

    private void addMessage(String text, boolean outgoing) {
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = ++messageId;
        message.message = text;
        message.date = (int) (System.currentTimeMillis() / 1000);
        message.out = outgoing;
        message.flags = 256;
        message.from_id = new TLRPC.TL_peerUser();
        message.from_id.user_id = outgoing ? 1 : 2;
        message.peer_id = new TLRPC.TL_peerUser();
        message.peer_id.user_id = outgoing ? 2 : 1;
        message.media = new TLRPC.TL_messageMediaEmpty();
        MessageObject object = new MessageObject(currentAccount, message, true, false) {
            @Override
            public boolean isOutOwner() { return outgoing; }
        };
        object.eventId = 1;
        ChatMessageCell cell = new ChatMessageCell(messages.getContext(), currentAccount);
        cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {});
        cell.setMessageObject(object, null, false, false, false);
        messages.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }
}
