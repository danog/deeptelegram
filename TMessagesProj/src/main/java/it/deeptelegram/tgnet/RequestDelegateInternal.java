package it.deeptelegram.tgnet;

public interface RequestDelegateInternal {
    void run(int response, int errorCode, String errorText);
}
