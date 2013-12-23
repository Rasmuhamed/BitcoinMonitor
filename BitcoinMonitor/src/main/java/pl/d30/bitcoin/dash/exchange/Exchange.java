package pl.d30.bitcoin.dash.exchange;

import android.content.Context;
import android.util.Log;

import com.google.gson.JsonObject;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;

import java.text.DecimalFormat;

import pl.d30.bitcoin.D30;
import pl.d30.bitcoin.R;

public abstract class Exchange {

    // virtual currencies:
    public static final int BTC = 0;
    public static final int LTC = 1;

    // exchanges:
    public static final int MTGOX = 0;
    public static final int BITSTAMP = 1;
    public static final int BTCE = 2;

    // fiat currencies:
    public static final int USD = 0;
    public static final int GBP = 1;
    public static final int CAD = 2;
    public static final int AUD = 3;
    public static final int PLN = 4;
    public static final int JPY = 5;
    public static final int EUR = 6;
    public static final int NOK = 7;
    public static final int SGD = 8;

    // prices:
    public static final int PRICE_LAST = 0;
    public static final int PRICE_BUY = 1;
    public static final int PRICE_SELL = 2;

    // priorities:
    public static final boolean PERCENTAGE = true;
    public static final boolean CURRENCY = false;

    // other:
    private static final long FRESHNESS = 30;

    protected Context context;
    protected LastValue lastValue;

    public Exchange(Context context) {
        this.context = context;
    }

    public void getTicker(int currency, int item, OnTickerDataAvailable cb) {
        if(
            lastValue!=null
            &&
            lastValue.isFresh()
            &&
            lastValue.getCurrency()==currency
            &&
            lastValue.getItem()==item

        ) {
            cb.onTicker(getId(), lastValue);

        } else downloadResponse( currency, item,  cb);
    }

    protected void downloadResponse(final int currency, final int item, final OnTickerDataAvailable cb) {
        Ion.with(context, getUrl(currency, item))
            .setHeader("User-Agent", "DashClock Bitcoin Monitor " + D30.getAppVersion(context) + ", " + D30.getDeviceInfo())
            .asJsonObject()
            .setCallback(new FutureCallback<JsonObject>() {
                @Override
                public void onCompleted(Exception e, JsonObject json) {
                if( e!=null ) Log.w(D30.LOG, e.toString());
                if( json!=null ) processResponse(json, currency, item, cb);
                }
            });
    }

    protected abstract void processResponse(JsonObject json, int currency, int item, OnTickerDataAvailable cb);
    protected abstract String getUrl(int currency, int item);

    public static String getPriceTypeName(int priceType) {
        switch( priceType ) {
            case PRICE_LAST: return "last";
            case PRICE_BUY:  return "buy";
            case PRICE_SELL: return "sell";
        }
        return null;
    }
    public static String getItemName(int item) {
        switch( item ) {
            case BTC: return "BTC";
            case LTC: return "LTC";
        }
        return null;
    }
    public static Integer getItemDrawable(int item) {
        switch( item ) {
            case BTC: return R.drawable.ic_btc;
            case LTC: return R.drawable.ic_ltc;
        }
        return null;
    }
    public static String getCurrencyName(int currency) {
        switch( currency ) {
            case USD: return "USD";
            case GBP: return "GBP";
            case JPY: return "JPY";
            case EUR: return "EUR";
            case PLN: return "PLN";
            case NOK: return "NOK";
            case AUD: return "AUD";
            case CAD: return "CAD";
            case SGD: return "SGD";
        }
        return null;
    }
    public static Exchange getExchange(int exchange, Context context) {
        switch( exchange ) {
            case Exchange.MTGOX:    return MtGoxExchange.getInstance(context);
            case Exchange.BITSTAMP: return BitStampExchange.getInstance(context);
            case Exchange.BTCE:     return BtceExchange.getInstance(context);
        }
        return null;
    }

    public abstract int getId();
    public abstract String getName();
    public abstract String getPrettyName();
    public abstract boolean isCurrencySupported(int currency);
    public abstract boolean isItemSupported(int item);


    public interface OnTickerDataAvailable {
        public void onTicker(int source, LastValue lastValue);
    }

    public class LastValue {

        private float lastValue;
        private float buyValue;
        private float sellValue;

        private int currency;
        private int item;

        private long ts = 0;
        private float amount = 1;
        private String prettyAmount = "1";

        public LastValue(float lastValue, int currency, int item) {
            constructorsCallMe(lastValue, currency, item);
        }
        public LastValue(String lastValue, int currency, int item) {
            constructorsCallMe(lastValue, currency, item);
        }
        private void constructorsCallMe(String lastValue, int currency, int item) {
            constructorsCallMe(convertToFloat(lastValue), currency, item);
        }
        private void constructorsCallMe(float lastValue, int currency, int item) {
            this.lastValue = lastValue;
            this.currency = currency;
            this.item = item;
        }

        // handle amount
        public void setAmount(String amount) throws Exception {
            this.amount = Float.parseFloat(amount);
            if( this.amount<=0 ) throw new Exception("amount cannot be set to zero or less");

            String[] tmp = amount.split("\\.");
            prettyAmount = tmp[0].replaceFirst("^0+(?!$)", "");
            if( prettyAmount.equals("") ) prettyAmount = "0";
            if( tmp.length>1 ) {
                String decimals = tmp[1].replaceAll("[0]+$", "");
                if(!decimals.equals("")) prettyAmount += "." + decimals;
            }
        }
        public String getPrettyAmount() {
            return prettyAmount;
        }


        public void setTimestamp(long timestamp) {
            this.ts = timestamp;
        }
        public boolean isFresh() {
            return ts!=0 && ts + FRESHNESS > System.currentTimeMillis() / 100;
        }


        public int getCurrency() { return currency; }
        public int getItem() { return item; }

        public void setBuyValue(String buyValue) {
            setBuyValue(convertToFloat(buyValue));
        }
        public void setBuyValue(float buyValue) {
            this.buyValue = buyValue;
        }
        public void setSellValue(String sellValue) {
            setSellValue(convertToFloat(sellValue));
        }
        public void setSellValue(float sellValue) {
            this.sellValue = sellValue;
        }

        // different getters for a value
        public float getFloat(int priceType) {
            float tmp;
            switch( priceType ) {
                case Exchange.PRICE_LAST: tmp = lastValue; break;
                case Exchange.PRICE_SELL: tmp = sellValue; break;
                case Exchange.PRICE_BUY: tmp = buyValue; break;
                default: tmp = 0;
            }
            return amount * tmp;
        }
        public String getCompact(int priceType) {
            float tmp = getFloat(priceType);

            return getFormattedValue(
                tmp<10 ? new DecimalFormat("#.##").format(tmp) : "" + Math.round(tmp),
                true
            );
        }
        public String getString(int priceType) {
            return getFormattedValue("" + getFloat(priceType), false);
        }



        // methods used internally
        private float convertToFloat(String strValue) {
            return Float.parseFloat(strValue);
        }
        private String getFormattedValue(String value, boolean compact) {
            switch( currency ) {
                // Prefixed:
                case USD: return "$" + value;
                case GBP: return "£" + value;
                case JPY: return "¥" + value;

                // Suffixed:
                case EUR: return value + "€";
                case PLN: return value + "zł";
                case NOK: return value + "kr";

                // Prefixed w/long form
                case AUD: return (compact ? "A$" : "AU$") + value;
                case CAD: return (compact ? "C$" : "CA$") + value;
                case SGD: return (compact ? "S$" : "SG$") + value;

                default: return null;
            }
        }
    }
}
