/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.tgnet;

import it.deeptelegram.messenger.FileLog;

import java.util.HashMap;

public class TLClassStore {
    private HashMap<Integer, Class> classStore;

    public TLClassStore() {
        classStore = new HashMap<>();

        classStore.put(TLRPC.TL_error.constructor, TLRPC.TL_error.class);
        classStore.put(TLRPC.TL_decryptedMessageService.constructor, TLRPC.TL_decryptedMessageService.class);
        classStore.put(TLRPC.TL_decryptedMessage.constructor, TLRPC.TL_decryptedMessage.class);
        classStore.put(TLRPC.TL_config.constructor, TLRPC.TL_config.class);
        classStore.put(TLRPC.TL_decryptedMessageLayer.constructor, TLRPC.TL_decryptedMessageLayer.class);
        classStore.put(TLRPC.TL_decryptedMessage_layer17.constructor, TLRPC.TL_decryptedMessage.class);
        classStore.put(TLRPC.TL_decryptedMessageService_layer8.constructor, TLRPC.TL_decryptedMessageService_layer8.class);
        classStore.put(TLRPC.TL_decryptedMessage_layer8.constructor, TLRPC.TL_decryptedMessage_layer8.class);
        classStore.put(TLRPC.TL_message_secret.constructor, TLRPC.TL_message_secret.class);
        classStore.put(TLRPC.TL_message_secret_old.constructor, TLRPC.TL_message_secret_old.class);
        classStore.put(TLRPC.TL_messageEncryptedAction.constructor, TLRPC.TL_messageEncryptedAction.class);
        classStore.put(TLRPC.TL_null.constructor, TLRPC.TL_null.class);

        classStore.put(TLRPC.TL_updateShortChatMessage.constructor, TLRPC.TL_updateShortChatMessage.class);
        classStore.put(TLRPC.TL_updates.constructor, TLRPC.TL_updates.class);
        classStore.put(TLRPC.TL_updateShortMessage.constructor, TLRPC.TL_updateShortMessage.class);
        classStore.put(TLRPC.TL_updateShort.constructor, TLRPC.TL_updateShort.class);
        classStore.put(TLRPC.TL_updatesCombined.constructor, TLRPC.TL_updatesCombined.class);
        classStore.put(TLRPC.TL_updateShortSentMessage.constructor, TLRPC.TL_updateShortSentMessage.class);
        classStore.put(TLRPC.TL_updatesTooLong.constructor, TLRPC.TL_updatesTooLong.class);
    }

    static TLClassStore store = null;

    public static TLClassStore Instance() {
        if (store == null) {
            store = new TLClassStore();
        }
        return store;
    }

    public TLObject TLdeserialize(NativeByteBuffer stream, int constructor, boolean exception) {
        Class objClass = classStore.get(constructor);
        if (objClass != null) {
            TLObject response;
            try {
                response = (TLObject) objClass.newInstance();
            } catch (Throwable e) {
                FileLog.e("tmessages", e);
                return null;
            }
            response.readParams(stream, exception);
            return response;
        }
        return null;
    }
}
