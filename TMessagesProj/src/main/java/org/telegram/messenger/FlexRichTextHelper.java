package org.telegram.messenger;

import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.tgnet.tl.TL_keyboard;

import java.util.ArrayList;

/** Text runs from a copy of a rich message; media, links and layout stay intact. */
public class FlexRichTextHelper {

    public static TL_iv.RichMessage copy(TL_iv.RichMessage source) {
        SerializedData output = new SerializedData();
        source.serializeToStream(output);
        SerializedData input = new SerializedData(output.toByteArray());
        TL_iv.RichMessage result = TL_iv.RichMessage.TLdeserialize(input, input.readInt32(true), true);
        input.cleanup();
        output.cleanup();
        return result;
    }

    public static ArrayList<TL_iv.textPlain> texts(TL_iv.RichMessage message) {
        ArrayList<TL_iv.textPlain> result = new ArrayList<>();
        collectBlocks(message.blocks, result);
        return result;
    }

    private static void collectText(TL_iv.RichText text, ArrayList<TL_iv.textPlain> result) {
        if (text == null) return;
        if (text instanceof TL_iv.textPlain) {
            result.add((TL_iv.textPlain) text);
        } else if (text instanceof TL_iv.textDiff) {
            collectText(((TL_iv.textDiff) text).text, result);
            collectText(((TL_iv.textDiff) text).old_text, result);
        } else {
            collectText(text.text, result);
            for (TL_iv.RichText child : text.texts) collectText(child, result);
        }
    }

    private static void collectBlocks(ArrayList<TL_iv.PageBlock> blocks, ArrayList<TL_iv.textPlain> result) {
        for (TL_iv.PageBlock block : blocks) {
            collectText(block.text, result);
            if (block.caption != null) {
                collectText(block.caption.text, result);
                collectText(block.caption.credit, result);
            }
            if (block instanceof TL_iv.pageBlockDetails) {
                TL_iv.pageBlockDetails details = (TL_iv.pageBlockDetails) block;
                collectText(details.title, result);
                collectBlocks(details.blocks, result);
            } else if (block instanceof TL_iv.pageBlockList) {
                for (TL_iv.PageListItem item : ((TL_iv.pageBlockList) block).items) {
                    if (item instanceof TL_iv.TL_pageListItemText) collectText(((TL_iv.TL_pageListItemText) item).text, result);
                    else collectBlocks(((TL_iv.TL_pageListItemBlocks) item).blocks, result);
                }
            } else if (block instanceof TL_iv.pageBlockOrderedList) {
                for (TL_iv.PageListOrderedItem item : ((TL_iv.pageBlockOrderedList) block).items) {
                    if (item instanceof TL_iv.TL_pageListOrderedItemText) collectText(((TL_iv.TL_pageListOrderedItemText) item).text, result);
                    else collectBlocks(((TL_iv.TL_pageListOrderedItemBlocks) item).blocks, result);
                }
            } else if (block instanceof TL_iv.pageBlockTable) {
                TL_iv.pageBlockTable table = (TL_iv.pageBlockTable) block;
                collectText(table.title, result);
                for (TL_iv.pageTableRow row : table.rows) {
                    for (TL_iv.pageTableCell cell : row.cells) collectText(cell.text, result);
                }
            } else if (block instanceof TL_iv.pageBlockBlockquoteBlocks) {
                TL_iv.pageBlockBlockquoteBlocks quote = (TL_iv.pageBlockBlockquoteBlocks) block;
                collectText(quote.caption, result);
                collectBlocks(quote.blocks, result);
            } else if (block instanceof TL_iv.pageBlockBlockquote) {
                collectText(((TL_iv.pageBlockBlockquote) block).caption, result);
            } else if (block instanceof TL_iv.pageBlockPullquote) {
                collectText(((TL_iv.pageBlockPullquote) block).caption, result);
            } else if (block instanceof TL_iv.pageBlockAuthorDate) {
                collectText(((TL_iv.pageBlockAuthorDate) block).author, result);
            } else if (block instanceof TL_iv.pageBlockCollage) {
                collectBlocks(((TL_iv.pageBlockCollage) block).items, result);
            } else if (block instanceof TL_iv.pageBlockSlideshow) {
                collectBlocks(((TL_iv.pageBlockSlideshow) block).items, result);
            } else if (block instanceof TL_iv.pageBlockEmbedPost) {
                collectBlocks(((TL_iv.pageBlockEmbedPost) block).blocks, result);
            } else if (block instanceof TL_iv.pageBlockCover) {
                ArrayList<TL_iv.PageBlock> cover = new ArrayList<>();
                cover.add(((TL_iv.pageBlockCover) block).cover);
                collectBlocks(cover, result);
            } else if (block instanceof TL_iv.pageBlockButtonRow) {
                for (TL_keyboard.PageButton button : ((TL_iv.pageBlockButtonRow) block).buttons) collectText(button.text, result);
            }
        }
    }
}
