/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package it.deeptelegram.messenger;

import org.telegram.tgnet.SerializedData;

public class MessageKeyData {

    public byte[] aesKey;
    public byte[] aesIv;

    public static MessageKeyData generateMessageKeyData(byte[] authKey, byte[] messageKey, boolean incoming) {
        MessageKeyData keyData = new MessageKeyData();
        if (authKey == null || authKey.length == 0) {
            keyData.aesIv = null;
            keyData.aesKey = null;
            return keyData;
        }

        int x = incoming ? 8 : 0;

        SerializedData data = new SerializedData();
        data.writeBytes(messageKey);
        data.writeBytes(authKey, x, 32);
        byte[] sha1_a = Utilities.computeSHA1(data.toByteArray());
        data.cleanup();

        data = new SerializedData();
        data.writeBytes(authKey, 32 + x, 16);
        data.writeBytes(messageKey);
        data.writeBytes(authKey, 48 + x, 16);
        byte[] sha1_b = Utilities.computeSHA1(data.toByteArray());
        data.cleanup();

        data = new SerializedData();
        data.writeBytes(authKey, 64 + x, 32);
        data.writeBytes(messageKey);
        byte[] sha1_c = Utilities.computeSHA1(data.toByteArray());
        data.cleanup();

        data = new SerializedData();
        data.writeBytes(messageKey);
        data.writeBytes(authKey, 96 + x, 32);
        byte[] sha1_d = Utilities.computeSHA1(data.toByteArray());
        data.cleanup();

        data = new SerializedData();
        data.writeBytes(sha1_a, 0, 8);
        data.writeBytes(sha1_b, 8, 12);
        data.writeBytes(sha1_c, 4, 12);
        keyData.aesKey = data.toByteArray();
        data.cleanup();

        data = new SerializedData();
        data.writeBytes(sha1_a, 8, 12);
        data.writeBytes(sha1_b, 0, 8);
        data.writeBytes(sha1_c, 16, 4);
        data.writeBytes(sha1_d, 0, 8);
        keyData.aesIv = data.toByteArray();
        data.cleanup();

        return keyData;
    }
}
