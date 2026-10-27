package org.telegram.messenger;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.URLSpan;

import org.telegram.utils.code.highlight.CodeHighlighting;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.URLSpanReplacement;

import org.telegram.tgnet.TLRPC;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FlexEntitiesHelper {

    private static final Pattern LINKS = Pattern.compile("```[\\s\\S]*?```|`[^`\\n]*`|\\[([^]\\n]+)]\\((https?://[^\\s)]+)\\)");

    public static void parseLinks(CharSequence[] message, MediaDataController controller, boolean allowStrike) {
        SpannableStringBuilder text = new SpannableStringBuilder(message[0]);
        Matcher matcher = LINKS.matcher(text);
        int from = 0;
        while (matcher.find(from)) {
            from = matcher.end();
            if (matcher.group(1) == null || text.getSpans(matcher.start(), matcher.end(), URLSpan.class).length > 0) {
                continue;
            }
            boolean code = text.getSpans(matcher.start(), matcher.end(), CodeHighlighting.Span.class).length > 0;
            for (TextStyleSpan span : text.getSpans(matcher.start(), matcher.end(), TextStyleSpan.class)) {
                code |= span.isMono();
            }
            if (code) continue;
            int start = matcher.start();
            String url = matcher.group(2);
            CharSequence[] label = {text.subSequence(matcher.start(1), matcher.end(1))};
            TLRPC.TL_textWithEntities formatted = new TLRPC.TL_textWithEntities();
            formatted.entities = controller.getEntities(label, allowStrike, true);
            formatted.text = label[0].toString();
            text.replace(start, matcher.end(), MessageObject.formatTextWithEntities(formatted));
            text.setSpan(new URLSpanReplacement(url), start, start + label[0].length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            from = start + label[0].length();
            matcher.reset(text);
        }
        message[0] = text;
    }
}
