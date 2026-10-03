package com.yuxiang.drawer;

import android.app.Activity;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class GuideSequence {
    private final Activity activity;
    private final List<Step> steps = new ArrayList<>();
    private int index;
    private boolean running;
    private boolean shown;
    private Runnable finishListener;

    public GuideSequence(Activity activity) {
        this.activity = activity;
    }

    public GuideSequence add(int targetId, int titleId, int textId) {
        steps.add(new Step(targetId, titleId, textId));
        return this;
    }

    public GuideSequence add(int targetId, int textId) {
        return add(targetId, 0, textId);
    }

    public GuideSequence setOnFinishListener(Runnable listener) {
        finishListener = listener;
        return this;
    }

    public boolean isRunning() {
        return running;
    }

    public void start() {
        if (running || steps.isEmpty()) {
            return;
        }
        running = true;
        index = 0;
        shown = false;
        showNext();
    }

    private void onStepEnd(boolean completed) {
        if (!completed) {
            running = false;
            return;
        }
        showNext();
    }

    private void showNext() {
        while (index < steps.size()) {
            final Step step = steps.get(index++);
            final View target = activity.findViewById(step.targetId);
            if (target == null) {
                continue;
            }
            final GuideOverlay overlay = new GuideOverlay(activity);
            overlay.setOnEndListener(this::onStepEnd);
            shown = true;
            overlay.show(target,
                    step.titleId == 0 ? null : activity.getString(step.titleId),
                    activity.getString(step.textId));
            return;
        }
        running = false;
        if (shown && finishListener != null) {
            final Runnable listener = finishListener;
            finishListener = null;
            listener.run();
        }
    }

    private static class Step {
        final int targetId;
        final int titleId;
        final int textId;

        Step(int targetId, int titleId, int textId) {
            this.targetId = targetId;
            this.titleId = titleId;
            this.textId = textId;
        }
    }
}
