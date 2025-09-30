package com.tencent.qcloud.tuikit.tuicallkit.demo.setting;

import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.tencent.cloud.tuikit.engine.call.TUICallDefine;
import com.tencent.qcloud.tuikit.tuicallkit.manager.CallManager;
import com.tencent.qcloud.tuikit.tuicallkit.state.UserState;
import com.tencent.qcloud.tuikit.tuicallkit.view.CallAdapter;

public class CustomUIAdapter {
    public CallAdapter getStreamViewAdapter() {
        return streamViewAdapter;
    }

    public CallAdapter getMainViewAdapter() {
        return mainViewAdapter;
    }

    private CallAdapter streamViewAdapter = new CallAdapter() {
        @Override
        public ViewGroup onCreateStreamView(ViewGroup view) {
            Button buttonHangup = new Button(view.getContext());
            buttonHangup.setText("Hangup");
            buttonHangup.setBackgroundColor(Color.CYAN);
            buttonHangup.setOnClickListener(v -> {
                UserState.User selfUser = CallManager.Companion.getInstance().getUserState().getSelfUser().get();
                if (selfUser.getCallRole() == TUICallDefine.Role.Called
                        && selfUser.getCallStatus().get() == TUICallDefine.Status.Waiting) {
                    CallManager.Companion.getInstance().reject(null);
                } else {
                    CallManager.Companion.getInstance().hangup(null);
                }
            });
            ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(200, 120);
            lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            buttonHangup.setLayoutParams(lp);
            view.addView(buttonHangup);
            return view;
        }
    };

    private CallAdapter mainViewAdapter = new CallAdapter() {
        @Override
        public ViewGroup onCreateMainView(ViewGroup view) {
            Button button = new Button(view.getContext());
            button.setBackgroundColor(Color.LTGRAY);
            ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(120, 120);
            lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            button.setLayoutParams(lp);
            view.addView(button);
            return view;
        }
    };
}
