/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package it.deeptelegram.ui.Components;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import it.deeptelegram.messenger.AndroidUtilities;
import it.deeptelegram.messenger.LocaleController;
import it.deeptelegram.messenger.MessagesController;
import it.deeptelegram.messenger.R;
import it.deeptelegram.messenger.support.widget.LinearLayoutManager;
import it.deeptelegram.messenger.support.widget.RecyclerView;
import it.deeptelegram.tgnet.ConnectionsManager;
import it.deeptelegram.tgnet.RequestDelegate;
import it.deeptelegram.tgnet.TLObject;
import it.deeptelegram.tgnet.TLRPC;
import it.deeptelegram.ui.ActionBar.BaseFragment;
import it.deeptelegram.ui.ActionBar.BottomSheet;
import it.deeptelegram.ui.ActionBar.Theme;
import it.deeptelegram.ui.Cells.JoinSheetUserCell;
import it.deeptelegram.ui.ChatActivity;

public class JoinGroupAlert extends BottomSheet {

    private TLRPC.ChatInvite chatInvite;
    private String hash;
    private BaseFragment fragment;

    public JoinGroupAlert(final Context context, TLRPC.ChatInvite invite, String group, BaseFragment parentFragment) {
        super(context, false);
        setApplyBottomPadding(false);
        setApplyTopPadding(false);

        fragment = parentFragment;
        chatInvite = invite;
        hash = group;

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setClickable(true);
        setCustomView(linearLayout);

        String title;
        AvatarDrawable avatarDrawable;
        TLRPC.FileLocation photo = null;
        int participants_count;

        if (invite.chat != null) {
            avatarDrawable = new AvatarDrawable(invite.chat);
            if (chatInvite.chat.photo != null) {
                photo = chatInvite.chat.photo.photo_small;
            }
            title = invite.chat.title;
            participants_count = invite.chat.participants_count;
        } else {
            avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(0, invite.title, null, false);
            if (chatInvite.photo != null) {
                photo = chatInvite.photo.photo_small;
            }
            title = invite.title;
            participants_count = invite.participants_count;
        }

        BackupImageView avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(35));
        avatarImageView.setImage(photo, "50_50", avatarDrawable);
        linearLayout.addView(avatarImageView, LayoutHelper.createLinear(70, 70, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));

        TextView textView = new TextView(context);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        textView.setTextColor(Theme.JOIN_SHEET_NAME_TEXT_COLOR);
        textView.setText(title);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 10, 10, 10, participants_count > 0 ? 0 : 10));

        if (participants_count > 0) {
            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTextColor(Theme.JOIN_SHEET_COUNT_TEXT_COLOR);
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setText(LocaleController.formatPluralString("Members", participants_count));
            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 10, 4, 10, 10));
        }

        if (!invite.participants.isEmpty()) {
            RecyclerListView listView = new RecyclerListView(context);
            listView.setPadding(0, 0, 0, AndroidUtilities.dp(8));
            listView.setNestedScrollingEnabled(false);
            listView.setClipToPadding(false);
            listView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
            listView.setHorizontalScrollBarEnabled(false);
            listView.setVerticalScrollBarEnabled(false);
            listView.setAdapter(new UsersAdapter(context));
            listView.setGlowColor(0x01ffffff);
            linearLayout.addView(listView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 90, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));
        }

        View shadow = new View(context);
        shadow.setBackgroundResource(R.drawable.header_shadow_reverse);
        linearLayout.addView(shadow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 3));

        PickerBottomLayout pickerBottomLayout = new PickerBottomLayout(context, false);
        linearLayout.addView(pickerBottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));
        pickerBottomLayout.cancelButton.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        pickerBottomLayout.cancelButton.setTextColor(Theme.STICKERS_SHEET_CLOSE_TEXT_COLOR);
        pickerBottomLayout.cancelButton.setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
        pickerBottomLayout.cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });
        pickerBottomLayout.doneButton.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        pickerBottomLayout.doneButton.setVisibility(View.VISIBLE);
        pickerBottomLayout.doneButtonBadgeTextView.setVisibility(View.GONE);
        pickerBottomLayout.doneButtonTextView.setTextColor(Theme.STICKERS_SHEET_CLOSE_TEXT_COLOR);
        pickerBottomLayout.doneButtonTextView.setText(LocaleController.getString("JoinGroup", R.string.JoinGroup));
        pickerBottomLayout.doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                TLRPC.TL_messages_importChatInvite req = new TLRPC.TL_messages_importChatInvite();
                req.hash = hash;
                ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(final TLObject response, final TLRPC.TL_error error) {
                        if (error == null) {
                            TLRPC.Updates updates = (TLRPC.Updates) response;
                            MessagesController.getInstance().processUpdates(updates, false);
                        }
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (fragment == null || fragment.getParentActivity() == null) {
                                    return;
                                }
                                if (error == null) {
                                    TLRPC.Updates updates = (TLRPC.Updates) response;
                                    if (!updates.chats.isEmpty()) {
                                        TLRPC.Chat chat = updates.chats.get(0);
                                        chat.left = false;
                                        chat.kicked = false;
                                        MessagesController.getInstance().putUsers(updates.users, false);
                                        MessagesController.getInstance().putChats(updates.chats, false);
                                        Bundle args = new Bundle();
                                        args.putInt("chat_id", chat.id);
                                        if (MessagesController.checkCanOpenChat(args, fragment)) {
                                            ChatActivity chatActivity = new ChatActivity(args);
                                            fragment.presentFragment(chatActivity, fragment instanceof ChatActivity);
                                        }
                                    }
                                } else {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
                                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                    if (error.text.startsWith("FLOOD_WAIT")) {
                                        builder.setMessage(LocaleController.getString("FloodWait", R.string.FloodWait));
                                    } else if (error.text.equals("USERS_TOO_MUCH")) {
                                        builder.setMessage(LocaleController.getString("JoinToGroupErrorFull", R.string.JoinToGroupErrorFull));
                                    } else {
                                        builder.setMessage(LocaleController.getString("JoinToGroupErrorNotExist", R.string.JoinToGroupErrorNotExist));
                                    }
                                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                                    fragment.showDialog(builder.create());
                                }
                            }
                        });
                    }
                }, ConnectionsManager.RequestFlagFailOnServerErrors);
            }
        });
    }

    private class Holder extends RecyclerView.ViewHolder {

        public Holder(View itemView) {
            super(itemView);
        }
    }

    private class UsersAdapter extends RecyclerView.Adapter {

        private Context context;

        public UsersAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            int count = chatInvite.participants.size();
            int participants_count;
            if (chatInvite.chat != null) {
                participants_count = chatInvite.chat.participants_count;
            } else {
                participants_count = chatInvite.participants_count;
            }
            if (count != participants_count) {
                count++;
            }
            return count;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = new JoinSheetUserCell(context);
            view.setLayoutParams(new RecyclerView.LayoutParams(AndroidUtilities.dp(100), AndroidUtilities.dp(90)));
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            JoinSheetUserCell cell = (JoinSheetUserCell) holder.itemView;
            if (position < chatInvite.participants.size()) {
                cell.setUser(chatInvite.participants.get(position));
            } else {
                int participants_count;
                if (chatInvite.chat != null) {
                    participants_count = chatInvite.chat.participants_count;
                } else {
                    participants_count = chatInvite.participants_count;
                }
                cell.setCount(participants_count - chatInvite.participants.size());
            }
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }
    }
}
