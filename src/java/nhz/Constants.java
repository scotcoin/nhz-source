package nhz;

import java.util.Calendar;
import java.util.TimeZone;

public final class Constants {

    public static final int BLOCK_HEADER_LENGTH = 232;
    public static final int MAX_NUMBER_OF_TRANSACTIONS = 255;
    public static final int MAX_PAYLOAD_LENGTH = MAX_NUMBER_OF_TRANSACTIONS * 160;
    public static final long MAX_BALANCE_NHZ = 1000000000;
    public static final long ONE_NHZ = 100000000;
    public static final long MAX_BALANCE_NQT = MAX_BALANCE_NHZ * ONE_NHZ;
    public static final long INITIAL_BASE_TARGET = 153722867;
    public static final long MAX_BASE_TARGET = MAX_BALANCE_NHZ * INITIAL_BASE_TARGET;

    public static final int MAX_ALIAS_URI_LENGTH = 1000;
    public static final int MAX_ALIAS_LENGTH = 100;

    public static final int MAX_ARBITRARY_MESSAGE_LENGTH = 1000;

    public static final int MAX_ACCOUNT_NAME_LENGTH = 100;
    public static final int MAX_ACCOUNT_DESCRIPTION_LENGTH = 1000;

    public static final long MAX_ASSET_QUANTITY_QNT = 1000000000L * 100000000L;
    public static final long ASSET_ISSUANCE_FEE_NQT = 1000 * ONE_NHZ;
    public static final int MIN_ASSET_NAME_LENGTH = 3;
    public static final int MAX_ASSET_NAME_LENGTH = 10;
    public static final int MAX_ASSET_DESCRIPTION_LENGTH = 1000;
    public static final int MAX_ASSET_TRANSFER_COMMENT_LENGTH = 1000;

    public static final int MAX_POLL_NAME_LENGTH = 100;
    public static final int MAX_POLL_DESCRIPTION_LENGTH = 1000;
    public static final int MAX_POLL_OPTION_LENGTH = 100;
    public static final int MAX_POLL_OPTION_COUNT = 100;

    public static final int MAX_DIGITAL_GOODS_QUANTITY = 1000000000;
    public static final int MAX_DIGITAL_GOODS_LISTING_NAME_LENGTH = 100;
    public static final int MAX_DIGITAL_GOODS_LISTING_DESCRIPTION_LENGTH = 1000;
    public static final int MAX_DIGITAL_GOODS_LISTING_TAGS_LENGTH = 100;
    public static final int MAX_DIGITAL_GOODS_NOTE_LENGTH = 1000;
    public static final int MAX_DIGITAL_GOODS_LENGTH = 1000;

    public static final int MAX_HUB_ANNOUNCEMENT_URIS = 100;
    public static final int MAX_HUB_ANNOUNCEMENT_URI_LENGTH = 1000;
    public static final long MIN_HUB_EFFECTIVE_BALANCE = 100000;

    public static final boolean isTestnet = Nhz.getBooleanProperty("nhz.isTestnet");

    public static final int ALIAS_SYSTEM_BLOCK = 22;
    public static final int TRANSPARENT_FORGING_BLOCK = 30;
    public static final int ARBITRARY_MESSAGES_BLOCK = 40;
    public static final int TRANSPARENT_FORGING_BLOCK_2 = 47;
    public static final int TRANSPARENT_FORGING_BLOCK_3 = 51;
    public static final int TRANSPARENT_FORGING_BLOCK_4 = 64;
    public static final int TRANSPARENT_FORGING_BLOCK_5 = 67;
	public static final int BLOCK_1000 = 1000;
    public static final int TRANSPARENT_FORGING_BLOCK_6 =  65000;
    public static final int TRANSPARENT_FORGING_BLOCK_7 =  Integer.MAX_VALUE;
    public static final int NQT_BLOCK = 67000;
    public static final int FRACTIONAL_BLOCK = 69000;
    public static final int ASSET_EXCHANGE_BLOCK = 70000;
    public static final int REFERENCED_TRANSACTION_FULL_HASH_BLOCK = 75000;
    public static final int REFERENCED_TRANSACTION_FULL_HASH_BLOCK_TIMESTAMP = Integer.MAX_VALUE;
    public static final int VOTING_SYSTEM_BLOCK = Integer.MAX_VALUE;
    public static final int DIGITAL_GOODS_STORE_BLOCK = Integer.MAX_VALUE;

    static final long UNCONFIRMED_POOL_DEPOSIT_NQT = (isTestnet ? 50 : 100) * ONE_NHZ;

	public static final long EPOCH_BEGINNING;
    static {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.set(Calendar.YEAR, 2014);
        calendar.set(Calendar.MONTH, Calendar.MARCH);
        calendar.set(Calendar.DAY_OF_MONTH, 22);
        calendar.set(Calendar.HOUR_OF_DAY, 22);
        calendar.set(Calendar.MINUTE, 22);
        calendar.set(Calendar.SECOND, 22);
        calendar.set(Calendar.MILLISECOND, 0);
        EPOCH_BEGINNING = calendar.getTimeInMillis();
    }

    public static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";

    private Constants() {} // never

}
