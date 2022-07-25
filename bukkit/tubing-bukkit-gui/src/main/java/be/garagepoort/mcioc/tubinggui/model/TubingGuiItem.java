package be.garagepoort.mcioc.tubinggui.model;

import be.garagepoort.mcioc.tubinggui.templates.xml.style.StyleId;

import java.util.Optional;

public class TubingGuiItem {

    private final StyleId id;
    private int slot;
    private final String leftClickAction;
    private final String rightClickAction;
    private final String leftShiftClickAction;
    private final String rightShiftClickAction;
    private final String middleClickAction;
    private final TubingGuiItemStack tubingGuiItemStack;
    private boolean hidden;

    public TubingGuiItem(StyleId id, int slot, String leftClickAction, String rightClickAction, String leftShiftClickAction, String rightShiftClickAction, String middleClickAction, TubingGuiItemStack itemStack, boolean hidden) {
        this.id = id;
        this.slot = slot;
        this.leftClickAction = leftClickAction;
        this.rightClickAction = rightClickAction;
        this.leftShiftClickAction = leftShiftClickAction;
        this.rightShiftClickAction = rightShiftClickAction;
        this.middleClickAction = middleClickAction;
        this.tubingGuiItemStack = itemStack;
        this.hidden = hidden;
    }

    public Optional<StyleId> getStyleId() {
        return Optional.ofNullable(id);
    }

    public String getLeftClickAction() {
        return leftClickAction;
    }

    public String getRightClickAction() {
        return rightClickAction;
    }

    public String getLeftShiftClickAction() {
        return leftShiftClickAction;
    }

    public String getRightShiftClickAction() {
        return rightShiftClickAction;
    }

    public String getMiddleClickAction() {
        return middleClickAction;
    }

    public int getSlot() {
        return slot;
    }

    public TubingGuiItemStack getTubingGuiItemStack() {
        return tubingGuiItemStack;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }

    public static class Builder {
        private final StyleId id;
        private final int slot;
        private String leftClickAction = TubingGuiActions.NOOP;
        private String rightClickAction = TubingGuiActions.NOOP;
        private String leftShiftClickAction = TubingGuiActions.NOOP;
        private String rightShiftClickAction = TubingGuiActions.NOOP;
        private String middleClickAction = TubingGuiActions.NOOP;
        private TubingGuiItemStack itemStack;
        private boolean hidden;

        public Builder(StyleId id, int slot) {
            this.id = id;
            this.slot = slot;
        }

        public Builder withLeftClickAction(String leftClickAction) {
            this.leftClickAction = leftClickAction;
            return this;
        }

        public Builder withRightClickAction(String rightClickAction) {
            this.rightClickAction = rightClickAction;
            return this;
        }

        public Builder withLeftShiftClickAction(String leftShiftClickAction) {
            this.leftShiftClickAction = leftShiftClickAction;
            return this;
        }

        public Builder withRightShiftClickAction(String rightShiftClickAction) {
            this.rightShiftClickAction = rightShiftClickAction;
            return this;
        }

        public Builder withMiddleClickAction(String middleClickAction) {
            this.middleClickAction = middleClickAction;
            return this;
        }


        public Builder withItemStack(TubingGuiItemStack itemStack) {
            this.itemStack = itemStack;
            return this;
        }

        public Builder withHidden(boolean hidden) {
            this.hidden = hidden;
            return this;
        }


        public TubingGuiItem build() {
            return new TubingGuiItem(id, slot, leftClickAction, rightClickAction, leftShiftClickAction, rightShiftClickAction, middleClickAction, itemStack, hidden);
        }
    }
}
