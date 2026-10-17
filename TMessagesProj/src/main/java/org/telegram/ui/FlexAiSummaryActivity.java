package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.app.DatePickerDialog;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FlexConfig;
import org.telegram.messenger.FlexLlmHelper;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.TranslateController;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TranslateAlert2;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;

public class FlexAiSummaryActivity extends UniversalFragment {

    private static final int ID_SCOPE_MODE = 1;
    private static final int ID_MESSAGE_COUNT = 2;
    private static final int ID_TIME_PRESET = 3;
    private static final int ID_START_DATE = 4;
    private static final int ID_END_DATE = 5;
    private static final int ID_GENERATE = 6;
    private static final int ID_COPY = 7;
    private static final int ID_SETTINGS = 8;

    private static final int SCOPE_RECENT_MESSAGES = 0;
    private static final int SCOPE_TIME_RANGE = 1;

    private static final int TIME_PRESET_6_HOURS = 0;
    private static final int TIME_PRESET_24_HOURS = 1;
    private static final int TIME_PRESET_3_DAYS = 2;
    private static final int TIME_PRESET_7_DAYS = 3;
    private static final int TIME_PRESET_CUSTOM = 4;

    private final long dialogId;
    private final long topicId;
    private final String chatTitle;

    private int scopeMode = SCOPE_RECENT_MESSAGES;
    private int recentMessageCount = 200;
    private int timePreset = TIME_PRESET_24_HOURS;
    private int customStartDate;
    private int customEndDate;
    private boolean generating;
    private String reportText;

    public FlexAiSummaryActivity(long dialogId, long topicId, String chatTitle) {
        this.dialogId = dialogId;
        this.topicId = topicId;
        this.chatTitle = TextUtils.isEmpty(chatTitle) ? getString(R.string.SummaryTitle) : chatTitle;
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.SummaryTitle);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.FlexAiSummarySelection)));
        items.add(UItem.asButton(0, R.drawable.msg2_discussion, getString(R.string.FlexAiSummaryCurrentChat), chatTitle));
        items.add(UItem.asButton(ID_SCOPE_MODE, R.drawable.outline_ai_translate2, getString(R.string.FlexAiSummaryScopeMode), getScopeModeValue()));
        if (scopeMode == SCOPE_RECENT_MESSAGES) {
            items.add(UItem.asButton(ID_MESSAGE_COUNT, R.drawable.msg_search, getString(R.string.FlexAiSummaryMessageCount), String.valueOf(recentMessageCount)));
        } else {
            items.add(UItem.asButton(ID_TIME_PRESET, R.drawable.msg_calendar2, getString(R.string.FlexAiSummaryTimeRange), getTimePresetValue()));
            if (timePreset == TIME_PRESET_CUSTOM) {
                items.add(UItem.asButton(ID_START_DATE, R.drawable.msg_calendar2, getString(R.string.FlexAiSummaryStartDate), formatDateValue(customStartDate)));
                items.add(UItem.asButton(ID_END_DATE, R.drawable.msg_calendar2, getString(R.string.FlexAiSummaryEndDate), formatDateValue(customEndDate)));
            }
        }
        items.add(UItem.asButton(ID_SETTINGS, R.drawable.outline_ai_translate2, getString(R.string.FlexAiSummarySettings)));
        items.add(UItem.asButton(ID_GENERATE, R.drawable.outline_ai_translate2, generating ? getString(R.string.FlexAiSummaryGenerating) : getString(R.string.FlexAiSummaryGenerate)));
        items.add(UItem.asShadow(getString(R.string.FlexAiSummaryFeatureInfo)));
        if (!TextUtils.isEmpty(reportText)) {
            items.add(UItem.asHeader(getString(R.string.FlexAiSummaryReport)));
            items.add(UItem.asButton(ID_COPY, R.drawable.msg_copy, getString(R.string.Copy)));
            items.add(UItem.asShadow(reportText));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_SCOPE_MODE) {
            showScopeModeDialog();
        } else if (item.id == ID_MESSAGE_COUNT) {
            showMessageCountDialog();
        } else if (item.id == ID_TIME_PRESET) {
            showTimePresetDialog();
        } else if (item.id == ID_START_DATE) {
            showDatePicker(true);
        } else if (item.id == ID_END_DATE) {
            showDatePicker(false);
        } else if (item.id == ID_SETTINGS) {
            presentFragment(new FlexLlmFeatureSettingsActivity(true));
        } else if (item.id == ID_GENERATE) {
            generateSummary();
        } else if (item.id == ID_COPY && !TextUtils.isEmpty(reportText)) {
            AndroidUtilities.addToClipboard(reportText);
            BulletinFactory.of(this).createCopyBulletin(getString(R.string.FlexAiSummaryCopied)).show();
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void showScopeModeDialog() {
        CharSequence[] items = new CharSequence[] {
            getString(R.string.FlexAiSummaryRecentMessages),
            getString(R.string.FlexAiSummaryTimeRange)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getString(R.string.FlexAiSummaryScopeMode));
        builder.setItems(items, (dialog, which) -> {
            scopeMode = which;
            listView.adapter.update(true);
        });
        showDialog(builder.create());
    }

    private void showMessageCountDialog() {
        showTextValueDialog(getString(R.string.FlexAiSummaryMessageCount), getString(R.string.FlexAiSummaryMessageCountHint), String.valueOf(recentMessageCount), InputType.TYPE_CLASS_NUMBER, false, value -> {
            int parsedCount;
            try {
                parsedCount = Integer.parseInt(value);
            } catch (Exception ignore) {
                parsedCount = 0;
            }
            if (parsedCount <= 0) {
                BulletinFactory.of(this).createErrorBulletin(getString(R.string.FlexAiSummaryMessageCountInvalid)).show();
                return;
            }
            recentMessageCount = parsedCount;
            listView.adapter.update(true);
        });
    }

    private void showTimePresetDialog() {
        CharSequence[] items = new CharSequence[] {
            getString(R.string.FlexAiSummaryLast6Hours),
            getString(R.string.FlexAiSummaryLast24Hours),
            getString(R.string.FlexAiSummaryLast3Days),
            getString(R.string.FlexAiSummaryLast7Days),
            getString(R.string.FlexAiSummaryCustomRange)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getString(R.string.FlexAiSummaryTimeRange));
        builder.setItems(items, (dialog, which) -> {
            timePreset = which;
            if (timePreset != TIME_PRESET_CUSTOM) {
                customStartDate = 0;
                customEndDate = 0;
            }
            listView.adapter.update(true);
        });
        showDialog(builder.create());
    }

    private void showDatePicker(boolean startDate) {
        Calendar calendar = Calendar.getInstance();
        int value = startDate ? customStartDate : customEndDate;
        if (value > 0) {
            calendar.setTimeInMillis(value * 1000L);
        }
        DatePickerDialog dialog = new DatePickerDialog(getContext(), (view, year, month, dayOfMonth) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(Calendar.YEAR, year);
            selected.set(Calendar.MONTH, month);
            selected.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            selected.set(Calendar.HOUR_OF_DAY, startDate ? 0 : 23);
            selected.set(Calendar.MINUTE, startDate ? 0 : 59);
            selected.set(Calendar.SECOND, startDate ? 0 : 59);
            selected.set(Calendar.MILLISECOND, startDate ? 0 : 999);
            int timestamp = (int) (selected.getTimeInMillis() / 1000L);
            if (startDate) {
                customStartDate = timestamp;
            } else {
                customEndDate = timestamp;
            }
            if (customStartDate > 0 && customEndDate > 0 && customEndDate < customStartDate) {
                BulletinFactory.of(this).createErrorBulletin(getString(R.string.FlexAiSummaryDateRangeInvalid)).show();
                if (startDate) {
                    customStartDate = 0;
                } else {
                    customEndDate = 0;
                }
            }
            listView.adapter.update(true);
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        showDialog(dialog);
    }

    private void generateSummary() {
        if (generating) {
            return;
        }
        if (scopeMode == SCOPE_TIME_RANGE && timePreset == TIME_PRESET_CUSTOM && (customStartDate == 0 || customEndDate == 0)) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.FlexAiSummarySelectDateRange)).show();
            return;
        }
        generating = true;
        listView.adapter.update(true);
        AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER, resourceProvider);
        showDialog(progressDialog);
        int startDate = getRequestStartDate();
        int endDate = getRequestEndDate();
        int limit = scopeMode == SCOPE_RECENT_MESSAGES ? recentMessageCount : 0;
        getMessagesStorage().getMessagesForAiSummary(dialogId, topicId, limit, startDate, endDate, messages -> {
            SummaryData summaryData = buildSummaryData(messages);
            if (summaryData == null) {
                generating = false;
                try {
                    progressDialog.dismiss();
                } catch (Exception ignore) {
                }
                listView.adapter.update(true);
                BulletinFactory.of(this).createErrorBulletin(getString(R.string.FlexAiSummaryNoMessages)).show();
                return;
            }
            FlexLlmHelper.requestText(
                FlexConfig.getAiSummaryLlmApiType(),
                FlexConfig.getAiSummaryLlmApiUrl(),
                FlexConfig.getAiSummaryLlmApiKey(),
                FlexConfig.getAiSummaryLlmModel(),
                FlexConfig.getAiSummaryLlmPrompt(),
                buildSummaryPrompt(summaryData),
                (result, error) -> {
                    generating = false;
                    try {
                        progressDialog.dismiss();
                    } catch (Exception ignore) {
                    }
                    if (result == null) {
                        listView.adapter.update(true);
                        BulletinFactory.of(this).createErrorBulletin(TextUtils.isEmpty(error) ? getString(R.string.FlexLlmRequestFailed) : error).show();
                        return;
                    }
                    reportText = buildFinalReport(summaryData, result);
                    listView.adapter.update(true);
                }
            );
        });
    }

    private SummaryData buildSummaryData(ArrayList<TLRPC.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        LinkedHashMap<String, SpeakerStat> speakerStats = new LinkedHashMap<>();
        StringBuilder transcript = new StringBuilder();
        int firstMessageDate = 0;
        int lastMessageDate = 0;
        int mediaMessages = 0;
        int analyzedMessages = 0;
        for (int i = 0; i < messages.size(); ++i) {
            TLRPC.Message message = messages.get(i);
            if (message == null || message.action != null) {
                continue;
            }
            MessageObject messageObject = new MessageObject(currentAccount, message, false, false);
            if (messageObject.isSponsored()) {
                continue;
            }
            CharSequence messageText = messageObject.caption != null && messageObject.caption.length() > 0 ? messageObject.caption : messageObject.messageText;
            String text = messageText == null ? "" : messageText.toString();
            text = text.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
            if (TextUtils.isEmpty(text)) {
                continue;
            }
            String senderName = resolveSenderName(message);
            SpeakerStat stat = speakerStats.get(senderName);
            if (stat == null) {
                stat = new SpeakerStat();
                stat.name = senderName;
                speakerStats.put(senderName, stat);
            }
            stat.messageCount++;
            stat.textChars += text.length();
            analyzedMessages++;
            if (!MessageObject.isMediaEmpty(message)) {
                mediaMessages++;
            }
            if (firstMessageDate == 0 || message.date < firstMessageDate) {
                firstMessageDate = message.date;
            }
            if (message.date > lastMessageDate) {
                lastMessageDate = message.date;
            }
            transcript.append('[')
                .append(formatDateTime(message.date))
                .append("] ")
                .append(senderName)
                .append(": ")
                .append(text)
                .append('\n');
        }
        if (analyzedMessages == 0 || transcript.length() == 0) {
            return null;
        }
        ArrayList<SpeakerStat> topSpeakers = new ArrayList<>(speakerStats.values());
        Collections.sort(topSpeakers, (left, right) -> {
            if (left.messageCount != right.messageCount) {
                return right.messageCount - left.messageCount;
            }
            return right.textChars - left.textChars;
        });
        SummaryData result = new SummaryData();
        result.scopeDescription = getScopeDescription();
        result.transcript = transcript.toString().trim();
        result.statsText = buildStatsText(analyzedMessages, speakerStats.size(), mediaMessages, firstMessageDate, lastMessageDate, topSpeakers);
        return result;
    }

    private String buildSummaryPrompt(SummaryData summaryData) {
        String language = TranslateAlert2.languageName(TranslateController.currentLanguage());
        if (TextUtils.isEmpty(language)) {
            language = TranslateController.currentLanguage();
        }
        StringBuilder prompt = new StringBuilder();
        prompt.append("Output language: ").append(language).append('\n');
        prompt.append("Chat title: ").append(chatTitle).append('\n');
        prompt.append("Scope: ").append(summaryData.scopeDescription).append('\n');
        prompt.append("Exact local statistics:\n").append(summaryData.statsText).append('\n').append('\n');
        prompt.append("Messages in chronological order:\n").append(summaryData.transcript);
        return prompt.toString();
    }

    private String buildFinalReport(SummaryData summaryData, String analysis) {
        StringBuilder builder = new StringBuilder();
        builder.append(getString(R.string.FlexAiSummaryReportStats)).append('\n');
        builder.append(summaryData.statsText).append('\n').append('\n');
        builder.append(getString(R.string.FlexAiSummaryReportAnalysis)).append('\n');
        builder.append(analysis.trim());
        return builder.toString();
    }

    private String buildStatsText(int analyzedMessages, int participantCount, int mediaMessages, int firstMessageDate, int lastMessageDate, ArrayList<SpeakerStat> topSpeakers) {
        StringBuilder builder = new StringBuilder();
        builder.append(getString(R.string.FlexAiSummaryScopeLabel)).append(": ").append(getScopeDescription()).append('\n');
        builder.append(getString(R.string.FlexAiSummaryMessagesAnalyzed)).append(": ").append(analyzedMessages).append('\n');
        builder.append(getString(R.string.FlexAiSummaryParticipants)).append(": ").append(participantCount).append('\n');
        builder.append(getString(R.string.FlexAiSummaryMediaMessages)).append(": ").append(mediaMessages).append('\n');
        if (firstMessageDate > 0 && lastMessageDate > 0) {
            builder.append(getString(R.string.FlexAiSummaryTimeSpan)).append(": ")
                .append(formatDateTime(firstMessageDate))
                .append(" - ")
                .append(formatDateTime(lastMessageDate))
                .append('\n');
        }
        builder.append(getString(R.string.FlexAiSummaryTopSpeakers)).append(':');
        int count = Math.min(10, topSpeakers.size());
        for (int i = 0; i < count; ++i) {
            SpeakerStat stat = topSpeakers.get(i);
            builder.append('\n')
                .append(i + 1)
                .append(". ")
                .append(stat.name)
                .append(" - ")
                .append(stat.messageCount)
                .append(' ')
                .append(getString(R.string.FlexAiSummaryMessagesUnit))
                .append(", ")
                .append(stat.textChars)
                .append(' ')
                .append(getString(R.string.FlexAiSummaryCharactersUnit));
        }
        return builder.toString();
    }

    private String resolveSenderName(TLRPC.Message message) {
        if (!TextUtils.isEmpty(message.post_author)) {
            return message.post_author;
        }
        long senderDialogId = DialogObject.getPeerDialogId(message.from_id);
        if (senderDialogId > 0) {
            TLRPC.User user = getMessagesController().getUser(senderDialogId);
            if (user != null) {
                return UserObject.getUserName(user);
            }
        } else if (senderDialogId < 0) {
            TLRPC.Chat chat = getMessagesController().getChat(-senderDialogId);
            if (chat != null && !TextUtils.isEmpty(chat.title)) {
                return chat.title;
            }
        }
        return getString(R.string.HiddenName);
    }

    private String getScopeModeValue() {
        return scopeMode == SCOPE_RECENT_MESSAGES ? getString(R.string.FlexAiSummaryRecentMessages) : getString(R.string.FlexAiSummaryTimeRange);
    }

    private String getTimePresetValue() {
        if (timePreset == TIME_PRESET_6_HOURS) {
            return getString(R.string.FlexAiSummaryLast6Hours);
        }
        if (timePreset == TIME_PRESET_3_DAYS) {
            return getString(R.string.FlexAiSummaryLast3Days);
        }
        if (timePreset == TIME_PRESET_7_DAYS) {
            return getString(R.string.FlexAiSummaryLast7Days);
        }
        if (timePreset == TIME_PRESET_CUSTOM) {
            return getString(R.string.FlexAiSummaryCustomRange);
        }
        return getString(R.string.FlexAiSummaryLast24Hours);
    }

    private String getScopeDescription() {
        if (scopeMode == SCOPE_RECENT_MESSAGES) {
            return getString(R.string.FlexAiSummaryRecentMessages) + ": " + recentMessageCount;
        }
        if (timePreset == TIME_PRESET_CUSTOM) {
            return formatDateValue(customStartDate) + " - " + formatDateValue(customEndDate);
        }
        return getTimePresetValue();
    }

    private String formatDateValue(int timestamp) {
        if (timestamp <= 0) {
            return getString(R.string.FlexLlmNotSet);
        }
        return LocaleController.getInstance().getFormatterDayMonth().format(new Date(timestamp * 1000L));
    }

    private String formatDateTime(int timestamp) {
        return LocaleController.getInstance().getFormatterStats().format(new Date(timestamp * 1000L));
    }

    private int getRequestStartDate() {
        if (scopeMode == SCOPE_RECENT_MESSAGES) {
            return 0;
        }
        if (timePreset == TIME_PRESET_CUSTOM) {
            return customStartDate;
        }
        Calendar calendar = Calendar.getInstance();
        if (timePreset == TIME_PRESET_6_HOURS) {
            calendar.add(Calendar.HOUR_OF_DAY, -6);
        } else if (timePreset == TIME_PRESET_3_DAYS) {
            calendar.add(Calendar.DAY_OF_MONTH, -3);
        } else if (timePreset == TIME_PRESET_7_DAYS) {
            calendar.add(Calendar.DAY_OF_MONTH, -7);
        } else {
            calendar.add(Calendar.HOUR_OF_DAY, -24);
        }
        return (int) (calendar.getTimeInMillis() / 1000L);
    }

    private int getRequestEndDate() {
        if (scopeMode == SCOPE_RECENT_MESSAGES) {
            return 0;
        }
        if (timePreset == TIME_PRESET_CUSTOM) {
            return customEndDate;
        }
        return (int) (System.currentTimeMillis() / 1000L);
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
        editText.setFocusable(true);
        editText.setImeOptions(multiline ? EditorInfo.IME_FLAG_NO_ENTER_ACTION : EditorInfo.IME_ACTION_DONE);
        editText.setBackgroundDrawable(null);
        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 12));

        builder.setView(container);
        builder.setWidth(AndroidUtilities.dp(multiline ? 320 : 292));
        builder.setPositiveButton(getString(R.string.Done), (dialog, which) -> onSave.run(editText.getText().toString().trim()));
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        editText.setSelection(editText.getText().length());
    }

    private static class SpeakerStat {
        private String name;
        private int messageCount;
        private int textChars;
    }

    private static class SummaryData {
        private String scopeDescription;
        private String statsText;
        private String transcript;
    }
}
