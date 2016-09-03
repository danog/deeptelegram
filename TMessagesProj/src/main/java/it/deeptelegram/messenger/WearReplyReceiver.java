/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package it.deeptelegram.messenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.RemoteInput;

public class WearReplyReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        ApplicationLoader.postInitApplication();
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput == null) {
            return;
        }
        CharSequence text = remoteInput.getCharSequence(NotificationsController.EXTRA_VOICE_REPLY);
        if (text == null || text.length() == 0) {
            return;
        }
        long dialog_id = intent.getLongExtra("dialog_id", 0);
        int max_id = intent.getIntExtra("max_id", 0);
        if (dialog_id == 0 || max_id == 0) {
            return;
        }
        SendMessagesHelper.getInstance().sendMessage(text.toString(), dialog_id, null, null, true, null, null, null);
        MessagesController.getInstance().markDialogAsRead(dialog_id, max_id, max_id, 0, true, false);
    }
}
