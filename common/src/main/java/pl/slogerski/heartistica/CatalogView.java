package pl.slogerski.heartistica;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CatalogView {
    interface Canvas {
        void fill(int x, int y, int right, int bottom, int color);
        void text(String text, int centerX, int y, int color);
        void heart(String id, int x, int y, int size);
        void clip(int x, int y, int right, int bottom);
        void unclip();
    }
    private record Card(String id, int x, int y, int size, int left, int right, int top, int bottom) {}
    private record Row(CatalogScroll scroll, int x, int y, int width, int top, int bottom) {}
    private final CatalogService service;
    private final HeartisticaConfig config;
    private final Map<String, CatalogScroll> offsets = new HashMap<>();
    private final CatalogScroll vertical = new CatalogScroll();
    private final List<Card> cards = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private Row dragging;
    private double grab;
    private int top, bottom;
    private boolean full;

    CatalogView(CatalogService service, HeartisticaConfig config) { this.service = service; this.config = config; }
    static int top(int height) { return Math.min(144, Math.max(96, height - 146)); }
    void render(Canvas canvas, int width, int y, int top, int bottom, boolean full) {
        render(canvas, width, y, top, bottom, full, System.nanoTime());
    }
    void render(Canvas canvas, int width, int y, int top, int bottom, boolean full, long now) {
        int statusBottom = bottom;
        if (full && !service.status().isEmpty()) bottom -= 14;
        this.top = top;
        this.bottom = bottom;
        this.full = full;
        cards.clear();
        rows.clear();
        if (bottom <= top) return;
        int stripWidth = Math.max(80, (int) (width * 0.6));
        stripWidth = Math.min(width - 16, stripWidth);
        int x = (width - stripWidth) / 2;
        if (full) {
            String selected = service.selected(config);
            int previewSize = top < 120 ? 24 : top < 144 ? 32 : 48;
            int previewY = top < 144 ? 56 : 66;
            canvas.heart(selected, width / 2 - previewSize / 2, previewY, previewSize);
            canvas.text(name(selected), width / 2, previewY + previewSize + 5, 0xFFFFFFFF);
        }
        List<HeartCatalog.Category> categories = full ? service.catalog().categories
                : service.catalog().categories.stream().filter(row -> row.id().equals("popular")).toList();
        vertical.limit(full ? categories.size() * 106 + 24 - (bottom - top) : 0);
        int offsetY = full ? (int) Math.round(vertical.step(now)) : 0;
        canvas.clip(0, top, width, bottom);
        int index = 0;
        for (var category : categories) {
            int rowY = y + index++ * 106 - offsetY;
            if (rowY + 100 <= top || rowY >= bottom) continue;
            String categoryName = category.id().equals("event") || category.id().equals("popular")
                    ? UiText.get(config.language, "heartistica.category." + category.id()) : category.name();
            if (full) canvas.text(categoryName, width / 2, rowY, 0xFFDDDDDD);
            int cardY = rowY + (full ? 15 : 0), size = 64, step = 72;
            CatalogScroll scroll = offsets.computeIfAbsent(category.id(), ignored -> new CatalogScroll());
            scroll.limit(Math.max(0, category.hearts().size() * step - 8 - stripWidth));
            int offset = (int) Math.round(scroll.step(now));
            int clipTop = Math.max(top, cardY), clipBottom = Math.min(bottom, cardY + size);
            if (clipBottom > clipTop) {
                canvas.clip(x, clipTop, x + stripWidth, clipBottom);
                if (category.hearts().isEmpty()) canvas.text(UiText.get(config.language, "heartistica.catalog.empty"), width / 2, cardY + 26, 0xFF999999);
                for (int i = 0; i < category.hearts().size(); i++) {
                    String id = category.hearts().get(i);
                    int cardX = x + i * step - offset;
                    if (cardX + size <= x || cardX >= x + stripWidth) continue;
                    service.request(id);
                    boolean needsUpdate = service.needsUpdate(id);
                    boolean selected = service.selected(config).equals(id);
                    canvas.fill(cardX, cardY, cardX + size, cardY + size, selected ? 0xFFCA6174 : 0xFF666666);
                    canvas.fill(cardX + 1, cardY + 1, cardX + size - 1, cardY + size - 1, 0xFF252831);
                    canvas.heart(needsUpdate ? service.preview(id) : id, cardX + 12, cardY + 5, 40);
                    if (needsUpdate) canvas.fill(cardX + 1, cardY + 1, cardX + size - 1, cardY + 48, 0x99000000);
                    String name = needsUpdate ? UiText.get(config.language, "heartistica.catalog.need_update") : name(id);
                    if (!needsUpdate && name.length() > 10) name = name.substring(0, 9) + "…";
                    canvas.text(name, cardX + size / 2, cardY + 50, 0xFFEEEEEE);
                    cards.add(new Card(id, cardX, cardY, size, x, x + stripWidth, clipTop, clipBottom));
                }
                canvas.unclip();
            }
            int sliderY = cardY + size + 5;
            canvas.fill(x, sliderY, x + stripWidth, sliderY + 2, 0xFF121318);
            int thumb = thumbWidth(scroll, stripWidth);
            int thumbX = x + (scroll.maximum() == 0 ? 0 : (int) Math.round((stripWidth - thumb) * scroll.step(now) / scroll.maximum()));
            canvas.fill(thumbX, sliderY, thumbX + thumb, sliderY + 2, scroll.maximum() == 0 ? 0xFF24262C : 0xFF454851);
            rows.add(new Row(scroll, x, sliderY, stripWidth, Math.max(top, cardY), Math.min(bottom, sliderY + 10)));
        }
        canvas.unclip();
        if (full && !service.status().isEmpty()) canvas.text(UiText.get(config.language, service.status()), width / 2, statusBottom - 10, 0xFFAAAAAA);
    }

    private String name(String id) {
        if (id.equals("resource_pack")) return UiText.get(config.language, "heartistica.style.resource_pack");
        var theme = service.catalog().themes.get(id);
        return theme == null ? id : theme.name();
    }

    boolean click(double x, double y, int button) {
        return click(x, y, button, 0);
    }

    boolean click(double x, double y, int button, int primaryButton) {
        if (button != primaryButton || y < top || y >= bottom) return false;
        for (Row row : rows) if (x >= row.x && x < row.x + row.width && y >= row.y - 2 && y < row.y + 10) {
            if (row.scroll.maximum() == 0) return true;
            dragging = row;
            int thumb = thumbWidth(row.scroll, row.width);
            double thumbX = row.x + (row.width - thumb) * row.scroll.target() / row.scroll.maximum();
            grab = x >= thumbX && x < thumbX + thumb ? x - thumbX : thumb / 2.0;
            drag(x);
            return true;
        }
        for (Card card : cards) if (x >= Math.max(card.left, card.x) && x < Math.min(card.right, card.x + card.size)
                && y >= card.top && y < card.bottom) {
            if (service.needsUpdate(card.id)) return true;
            CatalogService.select(config, card.id);
            service.request(card.id);
            return true;
        }
        return false;
    }

    boolean drag(double x) {
        if (dragging == null) return false;
        int width = thumbWidth(dragging.scroll, dragging.width);
        dragging.scroll.to((x - dragging.x - grab) / Math.max(1, dragging.width - width) * dragging.scroll.maximum());
        return true;
    }
    boolean release() { boolean wasDragging = dragging != null; dragging = null; return wasDragging; }
    boolean scrolling(double x, double y, double horizontal, double amount) {
        if (y < top || y >= bottom) return false;
        for (Row row : rows) if ((horizontal != 0 || !full) && x >= row.x && x < row.x + row.width
                && y >= row.top && y < row.bottom && row.scroll.maximum() > 0) {
            row.scroll.by(-(horizontal != 0 ? horizontal : amount) * 48);
            return true;
        }
        if (full) { vertical.by(-amount * 40); return true; }
        return false;
    }
    private static int thumbWidth(CatalogScroll scroll, int width) {
        return scroll.maximum() == 0 ? width : Math.max(18, (int) (width * width / (width + scroll.maximum())));
    }
}
