package com.woodpeckerbros.watchreminder.guardian;

final class GuardianContract {
    static final String MAIN_PACKAGE = "com.woodpeckerbros.watchreminder";
    static final String GUARDIAN_PACKAGE = "com.woodpeckerbros.watchreminder.guardian";
    static final String ACTION_PLAN = MAIN_PACKAGE + ".guardian.PLAN";
    static final String ACTION_ACK = MAIN_PACKAGE + ".guardian.ACK";
    static final String ACTION_REQUEST_PLAN = MAIN_PACKAGE + ".guardian.REQUEST_PLAN";
    static final String ACTION_RECOVER = MAIN_PACKAGE + ".guardian.RECOVER";
    static final String EXTRA_PAYLOAD = "payload";
    static final String EXTRA_KEY = "key";
    static final String EXTRA_REMINDER_ID = "reminder_id";
    static final String EXTRA_REMINDER_NAME = "reminder_name";
    static final String EXTRA_SCHEDULED_AT = "scheduled_at";
    static final String EXTRA_ORIGINAL_AT = "original_at";
    static final String EXTRA_DAY = "day";
    static final String EXTRA_SNOOZE = "snooze";

    private GuardianContract() { }
}
