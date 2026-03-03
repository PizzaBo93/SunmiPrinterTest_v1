package com.test.sunmiprintertest;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Build;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import android.webkit.WebViewClient;
import android.webkit.CookieManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import woyou.aidlservice.jiuiv5.IWoyouService;

import org.json.JSONObject;
import org.json.JSONArray;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private IWoyouService woyouService;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemUI();
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        initPrinterService();
        initWebView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (woyouService != null) {
            unbindService(connService);
        }
    }

    private final ServiceConnection connService = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            woyouService = IWoyouService.Stub.asInterface(service);
            Log.d(TAG, "Printer service connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            woyouService = null;
            Log.w(TAG, "Printer service disconnected");
        }
    };

    private void initPrinterService() {
        Intent intent = new Intent();
        intent.setPackage("woyou.aidlservice.jiuiv5");
        intent.setAction("woyou.aidlservice.jiuiv5.IWoyouService");
        bindService(intent, connService, Context.BIND_AUTO_CREATE);
    }

    private void initWebView() {
        webView = findViewById(R.id.webView);

        setupWebViewSettings();
        setupWebViewClient();
        setupWebChromeClient();

        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidPrinter");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void hideSystemUI() {
        // Ẩn status bar + navigation bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }

        // Đảm bảo layout vẽ phía sau system bars
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
            getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        }
    }

    private void setupWebViewSettings() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setAllowUniversalAccessFromFileURLs(true);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }
    }

    private void setupWebViewClient() {
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(
                        "if(window.onPrinterReady) window.onPrinterReady();",
                        null
                );
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                Log.e(TAG, "WebView Error: " + description);
            }
        });
    }

    private void setupWebChromeClient() {
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d("WebView Console",
                        consoleMessage.message() + " -- Line: " +
                                consoleMessage.lineNumber() + " of " +
                                consoleMessage.sourceId()
                );
                return true;
            }

            // Chặn confirm()
            @Override
            public boolean onJsConfirm(
                    WebView view,
                    String url,
                    String message,
                    android.webkit.JsResult result
            ) {
                result.confirm(); // luôn OK
                return true; // chặn popup mặc định
            }

            // Chặn alert()
            @Override
            public boolean onJsAlert(
                    WebView view,
                    String url,
                    String message,
                    android.webkit.JsResult result
            ) {
                result.confirm();
                return true;
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public boolean isPrinterReady() {
            return woyouService != null;
        }

        @JavascriptInterface
        public boolean canPrintKitchen() {
            return woyouService != null;
        }

        @JavascriptInterface
        public void printBill(String orderData) {
            runOnUiThread(() -> {
                try {
                    JSONObject order = new JSONObject(orderData);
                    MainActivity.this.printCustomerBill(order);
                } catch (Exception e) {
                    Log.e(TAG, "Print bill error", e);
                }
            });
        }

        @JavascriptInterface
        public void printKitchenBill(String orderData) {
            runOnUiThread(() -> {
                try {
                    Log.d(TAG, "Kitchen print data: " + orderData);
                    JSONObject order = new JSONObject(orderData);
                    MainActivity.this.printKitchenOrder(order);
                } catch (Exception e) {
                    Log.e(TAG, "Kitchen print error", e);
                }
            });
        }

        @JavascriptInterface
        public void printCashDrawerReport(String reportData) {
            runOnUiThread(() -> {
                try {
                    Log.d(TAG, "Cash drawer report data: " + reportData);
                    JSONObject report = new JSONObject(reportData);
                    MainActivity.this.printCashReport(report);
                } catch (Exception e) {
                    Log.e(TAG, "Cash report error", e);
                }
            });
        }

        @JavascriptInterface
        public void printTest() {
            runOnUiThread(() -> MainActivity.this.printTestBill());
        }

        @JavascriptInterface
        public void deleteOrder(String orderId) {
            runOnUiThread(() -> {
                webView.evaluateJavascript(
                        "if(window.handleDeleteOrderFromAndroid) window.handleDeleteOrderFromAndroid('" + orderId + "');",
                        null
                );
            });
        }
    }

    /**
     * Print customer bill - 80mm format
     */
    private void printCustomerBill(JSONObject order) {
        try {
            if (!checkPrinterReady()) return;

            // Header - Simple & Large
            woyouService.setAlignment(1, null);
            woyouService.printTextWithFont("PIZZA TIME\n", null, 40, null);
            woyouService.printText("--------------------------------\n", null);

            // Order info
            woyouService.setAlignment(0, null);
            String orderId = order.optString("id", "N/A");
            String orderType = order.optString("orderType", "N/A");
            String tableNumber = order.optString("tableNumber", "");
            String note = order.optString("note", "");

            woyouService.printTextWithFont("Don: #" + orderId + "\n", null, 28, null);
            woyouService.printTextWithFont("Loai: " + formatOrderTypeSimple(orderType) + "\n", null, 28, null);

            if (!tableNumber.isEmpty() && orderType.equals("dine_in")) {
                woyouService.printTextWithFont("Ban: " + tableNumber + "\n", null, 28, null);
            }

            if (!note.isEmpty()) {
                woyouService.printTextWithFont("Ghi chu: " + note + "\n", null, 24, null);
            }

            woyouService.printTextWithFont(getCurrentTime() + "\n", null, 24, null);
            woyouService.printText("--------------------------------\n", null);

            // Items - Larger font
            JSONArray items = order.getJSONArray("items");
            int subtotal = 0;

            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String name = item.getString("name");
                int qty = item.getInt("quantity");
                int price = item.getInt("price");
                int itemTotal = price * qty;
                subtotal += itemTotal;

                // Item name + quantity
                woyouService.printTextWithFont(name + " x" + qty + "\n", null, 28, null);

                // In sub-items nếu có (pizza trong combo)
                JSONArray subItems = item.optJSONArray("subItems");
                if (subItems != null && subItems.length() > 0) {
                    for (int j = 0; j < subItems.length(); j++) {
                        JSONObject subItem = subItems.getJSONObject(j);
                        String subName = subItem.getString("name");
                        int subPrice = subItem.getInt("price");

                        // In sub-item với font nhỏ hơn
                        String subLine = "  " + subName;
                        if (subPrice > 0) {
                            subLine += " (+" + formatMoney(subPrice) + "d)";
                        }
                        woyouService.printTextWithFont(subLine + "\n", null, 24, null);
                    }
                }

                // Price aligned right
                String priceLine = formatMoney(itemTotal) + "d";
                woyouService.setAlignment(2, null);
                woyouService.printTextWithFont(priceLine + "\n", null, 28, null);
                woyouService.setAlignment(0, null);
            }

            woyouService.printText("--------------------------------\n", null);

            // ⭐ THÊM PHẦN GIẢM GIÁ
            JSONObject discount = order.optJSONObject("discount");
            int discountAmount = 0;

            if (discount != null) {
                discountAmount = discount.optInt("amount", 0);
                String discountType = discount.optString("type", "");
                int discountValue = discount.optInt("value", 0);

                if (discountAmount > 0) {
                    // Hiển thị tạm tính
                    woyouService.setAlignment(0, null);
                    String subtotalLine = formatLine("Tam tinh:", formatMoney(subtotal) + "d");
                    woyouService.printTextWithFont(subtotalLine + "\n", null, 26, null);

                    // Hiển thị giảm giá
                    String discountLabel = "Giam gia";
                    if (discountType.equals("percent")) {
                        discountLabel += " (" + discountValue + "%)";
                    }
                    String discountLine = formatLine(discountLabel + ":", "-" + formatMoney(discountAmount) + "d");
                    woyouService.printTextWithFont(discountLine + "\n", null, 26, null);

                    woyouService.printText("--------------------------------\n", null);
                }
            }

            // Total - Large & Bold (sau khi trừ giảm giá)
            int finalTotal = order.optInt("total", subtotal);

            woyouService.printText("================================\n", null);
            woyouService.setAlignment(1, null);
            woyouService.printTextWithFont("TONG CONG\n", null, 32, null);
            woyouService.printTextWithFont(formatMoney(finalTotal) + "d\n", null, 40, null);
            woyouService.printText("================================\n", null);

            // Footer - Simple
            woyouService.printTextWithFont("Cam on quy khach!\n", null, 28, null);
            woyouService.lineWrap(3, null);

            cutPaper();
            Log.d(TAG, "Bill printed with discount");

        } catch (Exception e) {
            Log.e(TAG, "Customer bill error", e);
        }
    }

    /**
     * Format line with label and value (left-right alignment)


    /**
     * Print kitchen order - 80mm format
     */
    private void printKitchenOrder(JSONObject order) {
        try {
            if (!checkPrinterReady()) return;

            // Header - Bold & Large
            woyouService.setAlignment(1, null);
            woyouService.printTextWithFont("BEP\n", null, 48, null);

            // Order number - Very Large
            String orderId = order.optString("id", "N/A");
            woyouService.printTextWithFont("#" + orderId + "\n", null, 40, null);

            woyouService.printText("--------------------------------\n", null);

            // Order type / Table - Large
            woyouService.setAlignment(0, null);
            String orderType = order.optString("orderType", "N/A");
            String tableNumber = order.optString("tableNumber", "");

            String typeDisplay = formatOrderType(orderType);
            if (!tableNumber.isEmpty() && orderType.equals("dine_in")) {
                typeDisplay = "BAN " + tableNumber;
            }
            woyouService.printTextWithFont(typeDisplay + "\n", null, 32, null);

            // Time
            String time = order.optString("createdTime", getCurrentTime());
            woyouService.printTextWithFont(time + "\n", null, 24, null);

            woyouService.printText("--------------------------------\n", null);

            // Items - Large & Clear
            JSONArray items = order.getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String name = item.getString("name");
                int qty = item.getInt("quantity");

                // Kiểm tra nếu là combo hoặc sub-item
                boolean isCombo = item.optBoolean("isCombo", false);
                boolean isSubItem = item.optBoolean("isSubItem", false);

                String line;
                if (isSubItem) {
                    // Sub-item (pizza trong combo) - font nhỏ hơn, không có [số]
                    line = String.format("    %s\n", name);
                    woyouService.printTextWithFont(line, null, 28, null);
                } else {
                    // Item chính - hiển thị số lượng
                    line = String.format("[%dx] %s\n", qty, name);
                    woyouService.printTextWithFont(line, null, 32, null);
                }
            }

            // Note
            String note = order.optString("note", "");
            if (!note.isEmpty()) {
                woyouService.printText("--------------------------------\n", null);
                woyouService.printTextWithFont("GHI CHU:\n", null, 28, null);
                woyouService.printTextWithFont(note + "\n", null, 28, null);
            }

            woyouService.printText("--------------------------------\n", null);
            woyouService.lineWrap(3, null);

            cutPaper();
            Log.d(TAG, "Kitchen bill printed");

        } catch (Exception e) {
            Log.e(TAG, "Kitchen bill error", e);
        }
    }

    /**
     * Print cash report - 80mm format
     */
    private void printCashReport(JSONObject report) {
        try {
            if (!checkPrinterReady()) return;

            // Header
            woyouService.setAlignment(1, null);
            woyouService.printTextWithFont("BAO CAO CHOT KET\n", null, 36, null);

            String storeName = report.optString("storeName", "");
            if (!storeName.isEmpty()) {
                woyouService.printTextWithFont(storeName + "\n", null, 24, null);
            }

            String date = report.optString("date", getCurrentTime());
            woyouService.printTextWithFont(date + "\n", null, 24, null);
            woyouService.printText("================================\n", null);

            // Reset về left align cho toàn bộ
            woyouService.setAlignment(0, null);

            // 1. Tiền đầu ngày
            int openingBalance = report.optInt("openingBalance", 0);
            String line1 = formatLine("Tien dau ngay:", formatMoney(openingBalance) + "d");
            woyouService.printTextWithFont(line1 + "\n", null, 26, null);

            // 2. Doanh thu tiền mặt
            int cashSales = report.optInt("cashSales", 0);
            String line2 = formatLine("Doanh thu cash:", "+" + formatMoney(cashSales) + "d");
            woyouService.printTextWithFont(line2 + "\n", null, 26, null);

            // 3. Thu/Chi trong ngày
            int income = report.optInt("income", 0);
            int expense = report.optInt("expense", 0);

            if (income > 0) {
                String line3 = formatLine("Thu them:", "+" + formatMoney(income) + "d");
                woyouService.printTextWithFont(line3 + "\n", null, 26, null);
            }

            if (expense > 0) {
                String line4 = formatLine("Chi:", "-" + formatMoney(expense) + "d");
                woyouService.printTextWithFont(line4 + "\n", null, 26, null);
            }

            woyouService.printText("--------------------------------\n", null);

            // 4. Tiền trong két (Lý thuyết)
            int expectedBalance = report.optInt("expectedBalance", 0);
            String line5 = formatLine("Tien ket (ly thuyet):", formatMoney(expectedBalance) + "d");
            woyouService.printTextWithFont(line5 + "\n", null, 26, null);

            woyouService.printText("================================\n", null);

            // 5. Tiền thực tế - BOLD & LARGER
            int actualCash = report.optInt("actualCash", 0);
            String line6 = formatLine("TIEN THUC TE:", formatMoney(actualCash) + "d");
            woyouService.printTextWithFont(line6 + "\n", null, 30, null);

            // 6. Chênh lệch
            int difference = report.optInt("difference", 0);
            String diffLabel = difference >= 0 ? "DU TIEN:" : "THIEU TIEN:";
            String diffValue = (difference >= 0 ? "+" : "") + formatMoney(Math.abs(difference)) + "d";
            String line7 = formatLine(diffLabel, diffValue);
            woyouService.printTextWithFont(line7 + "\n", null, 28, null);

            woyouService.printText("================================\n", null);

            // 7. Tiền bỏ lại trong két
            int keepInDrawer = report.optInt("keepInDrawer", 0);
            String line8 = formatLine("Tien bo lai ket:", formatMoney(keepInDrawer) + "d");
            woyouService.printTextWithFont(line8 + "\n", null, 26, null);
            woyouService.printTextWithFont("(Tien dau ngay mai)\n", null, 22, null);

            woyouService.printText("--------------------------------\n", null);

            // 8. Tiền nhận về - BOLD & LARGEST
            int cashReceived = report.optInt("cashReceived", 0);
            String line9 = formatLine("TIEN NHAN VE:", formatMoney(cashReceived) + "d");
            woyouService.printTextWithFont(line9 + "\n", null, 32, null);

            woyouService.printText("================================\n", null);

            // Chi tiết giao dịch (nếu có)
            JSONArray transactions = report.optJSONArray("transactions");
            if (transactions != null && transactions.length() > 0) {
                woyouService.printTextWithFont("CHI TIET THU/CHI\n", null, 26, null);
                woyouService.printText("- - - - - - - - - - - - - - - -\n", null);

                for (int i = 0; i < transactions.length(); i++) {
                    JSONObject t = transactions.getJSONObject(i);
                    String type = t.getString("type");
                    int amount = t.getInt("amount");
                    String note = t.getString("note");
                    String time = t.optString("time", "");

                    String sign = type.equals("income") ? "+" : "-";

                    // Note + time
                    woyouService.printTextWithFont(note + " (" + time + ")\n", null, 22, null);

                    // Amount aligned right on same line concept
                    String amountLine = "  " + sign + formatMoney(amount) + "d";
                    woyouService.setAlignment(2, null);
                    woyouService.printTextWithFont(amountLine + "\n", null, 24, null);
                    woyouService.setAlignment(0, null);
                }

                woyouService.printText("--------------------------------\n", null);
            }

            // Footer
            String staff = report.optString("staffName", "N/A");
            woyouService.printTextWithFont("Nhan vien: " + staff + "\n", null, 24, null);
            woyouService.printTextWithFont("In luc: " + getCurrentTime() + "\n", null, 22, null);

            woyouService.lineWrap(3, null);

            cutPaper();
            Log.d(TAG, "Cash report printed successfully");

        } catch (Exception e) {
            Log.e(TAG, "Cash report print error", e);
        }
    }

    /**
     * Format line with label and value (left-right alignment)
     * For 80mm paper (~32 characters at font size 26)
     */
    private String formatLine(String label, String value) {
        int totalWidth = 32; // Approximate characters for 80mm at size 26
        int valueWidth = value.length();
        int labelWidth = label.length();

        // Calculate spaces needed
        int spacesNeeded = totalWidth - labelWidth - valueWidth;
        if (spacesNeeded < 1) spacesNeeded = 1;

        StringBuilder line = new StringBuilder(label);
        for (int i = 0; i < spacesNeeded; i++) {
            line.append(" ");
        }
        line.append(value);

        return line.toString();
    }

    /**
     * Print test bill
     */
    private void printTestBill() {
        try {
            if (!checkPrinterReady()) return;

            woyouService.setAlignment(1, null);
            woyouService.printTextWithFont("TEST PRINT\n", null, 40, null);
            woyouService.printText("================================\n", null);
            woyouService.setAlignment(0, null);
            woyouService.printTextWithFont("May in hoat dong tot\n", null, 28, null);
            woyouService.printTextWithFont(getCurrentTime() + "\n", null, 24, null);
            woyouService.lineWrap(3, null);

            cutPaper();
            Log.d(TAG, "Test printed");

        } catch (Exception e) {
            Log.e(TAG, "Test print error", e);
        }
    }

    private boolean checkPrinterReady() {
        if (woyouService == null) {
            Log.w(TAG, "Printer not ready");
            return false;
        }
        return true;
    }

    private void cutPaper() {
        try {
            woyouService.sendRAWData(new byte[]{0x1D, 0x56, 0x42, 0x00}, null);
        } catch (Exception e) {
            Log.e(TAG, "Cut paper error", e);
        }
    }

    private String formatOrderType(String type) {
        switch (type) {
            case "dine_in":
                return "AN TAI CHO";
            case "takeaway":
                return "MANG VE";
            case "grab":
                return "GRAB";
            case "shopee":
                return "SHOPEE";
            default:
                return type.toUpperCase();
        }
    }

    private String formatOrderTypeSimple(String type) {
        switch (type) {
            case "dine_in":
                return "An tai cho";
            case "takeaway":
                return "Mang ve";
            case "grab":
                return "Grab";
            case "shopee":
                return "Shopee";
            default:
                return type;
        }
    }

    private String formatMoney(int amount) {
        return String.format("%,d", amount).replace(",", ".");
    }

    private String getCurrentTime() {
        return new java.text.SimpleDateFormat("HH:mm dd/MM/yyyy", java.util.Locale.getDefault())
                .format(new java.util.Date());
    }
}