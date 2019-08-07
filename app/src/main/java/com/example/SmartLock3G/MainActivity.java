package com.example.SmartLock3G;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.example.SmartLock3G.CH340Ser.MyApp;
import com.example.SmartLock3G.Track.trackAllActivity;
import com.example.SmartLock3G.tools.gpsinfo;
import com.example.SmartLock3G.utils.DateUtil;
import com.example.SmartLock3G.utils.Pref;
import com.lpoint.tcpsocketlib.TcpClient;
import com.lpoint.tcpsocketlib.TcpSocketListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cn.wch.ch34xuartdriver.CH34xUARTDriver;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    public LocationClient mLocationClient = null;
    public Button bt_send, mStart, sendgps;
    TcpClient tcpClient;
    String thisGpsInfos;
    Boolean nowsend = false;
    private MyLocationListener myListener = new MyLocationListener();
    private TextView tv_content;
    private EditText ed_send_text, EDIP, EDPORT;
    private String ThisPhoneIP = "";
    private List<gpsinfo> gpsdata;
    private Pref sp;
    private static final String ACTION_USB_PERMISSION = "com.example.SmartLock3G.USB_PERMISSION";


    private EditText writeText;
    private boolean isOpen;
    private Handler handler1;

    private Button writeButton, openButton, clearButton;

    public byte[] writeBuffer;
    public byte[] readBuffer;

    /**
     * 设置参数
     */
    private final int BAUDRATE = 9600;//波特率

    private final byte STOPBIT = 1;//停止位
    private final byte DATABIT = 8;//数据位
    private final byte PARITY  = 0;//奇偶校验位
    private final byte FLOW_CONTROL = 0;//停止位

    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 0) {
                mStart.setText("断开连接");
            } else if (msg.what == 1) {
                Toast.makeText(MainActivity.this, "无法连接", Toast.LENGTH_SHORT).show();
                mStart.setText("连接");
            } else if (msg.what == 3) {
                tcpClient.sendMsg("test");
            } else if (msg.what == 4) {
                nowsend = true;
                sendgps.setText("已开启发送坐标");
            } else if (msg.what == 5) {
                nowsend = false;
                sendgps.setText("已关闭发送坐标");
                
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_tcp);
        InitView();
        InitDate();
        initLocation();
        MyApp.driver = new CH34xUARTDriver(
                (UsbManager) getSystemService(Context.USB_SERVICE), this,
                ACTION_USB_PERMISSION);
        initUI();
        if (!MyApp.driver.UsbFeatureSupported())// 判断系统是否支持USB HOST
        {
            Dialog dialog = new AlertDialog.Builder(MainActivity.this)
                    .setTitle("提示")
                    .setMessage("您的手机不支持USB HOST，请更换其他手机再试！")
                    .setPositiveButton("确认",
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface arg0,
                                                    int arg1) {
                                    System.exit(0);
                                }
                            }).create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);// 保持常亮的屏幕的状态
        writeBuffer = new byte[512];
        readBuffer = new byte[512];
        isOpen = false;
        writeButton.setEnabled(false);

        //打开流程主要步骤为ResumeUsbList，UartInit
        openButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                if (!isOpen) {
                    if (!MyApp.driver.ResumeUsbList())// ResumeUsbList方法用于枚举CH34X设备以及打开相关设备
                    {
                        Toast.makeText(MainActivity.this, "打开设备失败!",
                                Toast.LENGTH_SHORT).show();
                        MyApp.driver.CloseDevice();
                    } else {
                        if (!MyApp.driver.UartInit()) {//对串口设备进行初始化操作
                            Toast.makeText(MainActivity.this, "设备初始化失败!",
                                    Toast.LENGTH_SHORT).show();
                            Toast.makeText(MainActivity.this, "打开" +
                                            "设备失败!",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        else {


                            if (MyApp.driver.SetConfig(BAUDRATE, DATABIT, STOPBIT, PARITY,//配置串口波特率，函数说明可参照编程手册
                                    FLOW_CONTROL)) {
                                Toast.makeText(MainActivity.this, "打开设备和串口设置成功!",
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(MainActivity.this, "打开设备成功，串口设置失败!",
                                        Toast.LENGTH_SHORT).show();
                            }
                            Toast.makeText(MainActivity.this, "打开设备成功!",
                                    Toast.LENGTH_SHORT).show();
                            isOpen = true;
                            openButton.setText("Close");
                            writeButton.setEnabled(true);
                            new readThread().start();//开启读线程读取串口接收的数据
                        }
                    }
                } else {
                    MyApp.driver.CloseDevice();
                    openButton.setText("Open");
                    writeButton.setEnabled(false);
                    isOpen = false;
                }
            }
        });


        writeButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                byte[] to_send = toByteArray(writeText.getText().toString());
                int retval = MyApp.driver.WriteData(to_send, to_send.length);//写数据，第一个参数为需要发送的字节数组，第二个参数为需要发送的字节长度，返回实际发送的字节长度
                if (retval < 0)
                    Toast.makeText(MainActivity.this, "写失败!",
                            Toast.LENGTH_SHORT).show();
            }
        });

        handler1 = new Handler() {

            public void handleMessage(Message msg) {

                tv_content.append((String) msg.obj);

            }
        };
    }

    private void InitDate() {
        mLocationClient = new LocationClient(getApplicationContext());
        //声明LocationClient类
        mLocationClient.registerLocationListener(myListener);
        //注册监听函数
        gpsdata = new ArrayList<gpsinfo>();
        ThisPhoneIP = getLocalIpAddress();  //获取本机IP
    }

    private void initLocation() {

//定位服务的客户端。宿主程序在客户端声明此类，并调用，目前只支持在主线程中启动
        mLocationClient = new LocationClient(this);
        //注册监听函数
        mLocationClient.registerLocationListener(myListener);
        //声明LocationClient类实例并配置定位参数
        LocationClientOption locationOption = new LocationClientOption();

        //可选，默认高精度，设置定位模式，高精度，低功耗，仅设备
        locationOption.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
        //可选，默认gcj02，设置返回的定位结果坐标系，如果配合百度地图使用，建议设置为bd09ll;
        locationOption.setCoorType("bd09ll");
        //可选，默认0，即仅定位一次，设置发起连续定位请求的间隔需要大于等于1000ms才是有效的
        locationOption.setScanSpan(1000);
        //可选，设置是否需要地址信息，默认不需要
        locationOption.setIsNeedAddress(true);
        //可选，设置是否需要地址描述
        locationOption.setIsNeedLocationDescribe(true);
        //可选，设置是否需要设备方向结果
        locationOption.setNeedDeviceDirect(true);
        //可选，默认false，设置是否当gps有效时按照1S1次频率输出GPS结果
        locationOption.setLocationNotify(true);
        //可选，默认true，定位SDK内部是一个SERVICE，并放到了独立进程，设置是否在stop的时候杀死这个进程，默认不杀死
        locationOption.setIgnoreKillProcess(true);
        //可选，默认false，设置是否需要位置语义化结果，可以在BDLocation.getLocationDescribe里得到，结果类似于“在北京天安门附近”
        locationOption.setIsNeedLocationDescribe(true);
        //可选，默认false，设置是否需要POI结果，可以在BDLocation.getPoiList里得到
        locationOption.setIsNeedLocationPoiList(false);
        //可选，默认false，设置是否收集CRASH信息，默认收集
        locationOption.SetIgnoreCacheException(false);
        //可选，默认false，设置是否开启Gps定位
        locationOption.setOpenGps(true);
        //可选，默认false，设置定位时是否需要海拔信息，默认不需要，除基础定位版本都可用
        locationOption.setIsNeedAltitude(true);
        //设置打开自动回调位置模式，该开关打开后，期间只要定位SDK检测到位置变化就会主动回调给开发者，该模式下开发者无需再关心定位间隔是多少，定位SDK本身发现位置变化就会及时回             调给开发者
        //locationOption.setOpenAutoNotifyMode();
        //设置打开自动回调位置模式，该开关打开后，期间只要定位SDK检测到位置变化就会主动回调给开发者
        locationOption.setOpenAutoNotifyMode(1000, 1, LocationClientOption.LOC_SENSITIVITY_HIGHT);
        //需将配置好的LocationClientOption对象，通过setLocOption方法传递给LocationClient对象使用
        mLocationClient.setLocOption(locationOption);
        //开始定位
        mLocationClient.start();
    }

    private void InitView() {
        EDIP = (EditText) findViewById(R.id.EDIP);
        EDPORT = (EditText) findViewById(R.id.EDPORT);
        tv_content = (TextView) findViewById(R.id.tv_content);
        bt_send = (Button) findViewById(R.id.bt_send);
        ed_send_text = (EditText) findViewById(R.id.ed_send_text);
        bt_send.setOnClickListener(this);
        mStart = (Button) findViewById(R.id.mStart);
        sendgps = (Button) findViewById(R.id.sendgps);
        mStart.setOnClickListener(this);
        sp = Pref.getInstance(this);
        EDIP.setText(sp.getHost());
        ed_send_text.setText(sp.getIdCode());
        EDPORT.setText(String.valueOf(sp.getPort()));

    }
    private void initUI() {
        writeText = (EditText) findViewById(R.id.WriteValues);
        writeButton = (Button) findViewById(R.id.WriteButton);
        openButton = (Button) findViewById(R.id.open_device);
        clearButton = (Button) findViewById(R.id.clearButton);
        clearButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                tv_content.setText("");
            }
        });
        return;
    }
    @Override
    protected void onStart() {
        super.onStart();
        // 适配android M，检查权限
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isNeedRequestPermissions(permissions)) {
            requestPermissions(permissions.toArray(new String[permissions.size()]), 0);
        }
    }

    //检查权限
    private boolean isNeedRequestPermissions(List<String> permissions) {
        // 定位精确位置
        addPermission(permissions, Manifest.permission.ACCESS_FINE_LOCATION);
        // 存储权限
        addPermission(permissions, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        // 读取手机状态
        addPermission(permissions, Manifest.permission.READ_PHONE_STATE);
        return permissions.size() > 0;
    }

    private void addPermission(List<String> permissionsList, String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(permission);
        }
    }

    public void sendGPS(View view) {
//        if (mStart.getText().equals("断开连接")) {
//            gpsdata.clear();
//            tcpClient.sendMsg(thisGpsInfos);
//        } else Toast.makeText(this, "Socket未连接", Toast.LENGTH_SHORT).show();
        if (nowsend) {
            Message msg = new Message();
            msg.what = 5;
            handler.sendMessage(msg);
        } else {
            Message msg = new Message();
            msg.what = 4;
            handler.sendMessage(msg);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bt_send:  //发送
                if (mStart.getText().equals("断开连接")) {
                    String str = ed_send_text.getText().toString();
                    if (str.equals("")) {
                        Toast.makeText(this, "请输入数据", Toast.LENGTH_SHORT).show();
                    } else
                        tcpClient.sendMsg(str);
                } else Toast.makeText(this, "Socket未连接", Toast.LENGTH_SHORT).show();
                break;
            case R.id.mStart:  //  连接/断开服务器
                tcpClient = new TcpClient(EDIP.getText().toString(), Integer.parseInt(EDPORT.getText().toString()));
                if (mStart.getText().equals("连接")) {
                    tcpClient.startConn();
                    SocketListener();
                    //保存host和port
                    sp.setHost(EDIP.getText().toString());
                    sp.setPort(Integer.parseInt(EDPORT.getText().toString()));
                    mStart.setText("断开连接");

                } else {
                    tcpClient.closeTcpSocket();
                    tv_content.setText("已清空");
                    mStart.setText("连接");
                }
                break;
        }

    }

    /**
     * 获取WIFI下ip地址
     */
    private String getLocalIpAddress() {
        WifiManager wifiManager = (WifiManager) this.getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        // 获取32位整型IP地址
        int ipAddress = wifiInfo.getIpAddress();
        Log.d("wifi", "本机	IP	" + String.format("%d.%d.%d.%d",
                (ipAddress & 0xff), (ipAddress >> 8 & 0xff),
                (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff)));
        //返回整型地址转换成“*.*.*.*”地址
        return String.format("%d.%d.%d.%d",
                (ipAddress & 0xff), (ipAddress >> 8 & 0xff),
                (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
    }

    public void SocketListener() {
        TcpSocketListener tcpSocketListener = new TcpSocketListener() {
            /**
               * 发起TCP连接时报出的异常
               */
            @Override
            public void onConnException(Exception e) {
                Log.d("wifi", "发起TCP连接时报出的异常   ");
                Message msg = new Message();
                msg.what = 1;
                handler.sendMessage(msg);

            }

            /**
               * 当TCP通道收到消息时执行此回调
               */
            @Override
            public void onMessage(String s) {
                Log.d("wifi", "收到数据   " + s.trim());

                tv_content.append("\n");
                tv_content.append(DateUtil.formatTime());
                tv_content.append("=>\r\r");
                tv_content.append(s.trim());

            }

            /**
               * 当TCP消息监听时遇到异常，从这里抛出
               */
            @Override
            public void onListenerException(Exception e) {
                Log.d("wifi", "TCP消息监听时遇到异常，从这里抛出  ");
                Message msg = new Message();
                msg.what = 1;
                handler.sendMessage(msg);
            }

            /**
               * 当sendMsg()方法成功执行完毕后，执行此方法
               */
            @Override
            public void onSendMsgSuccess(String s) {
                Message msg = new Message();
                msg.what = 0;
                handler.sendMessage(msg);
            }

            /**
               * 发送消息时遇到异常，从这里抛出
               */
            @Override
            public void onSendMsgException(Exception e) {
                Log.d("wifi", "发送消息时遇到异常，从这里抛出 ");
                Message msg = new Message();
                msg.what = 1;
                handler.sendMessage(msg);

            }

            /**
               * 当TCP连接断开时遇到异常，从这里抛出
               */
            @Override
            public void onCloseException(Exception e) {
                Log.d("wifi", "当TCP连接断开时遇到异常，从这里抛出 ");
            }
        };
        tcpClient.setTcpSocketListener(tcpSocketListener);
    }

    @Override
    protected void onDestroy() {

        tcpClient.closeTcpSocket();
        super.onDestroy();
    }

    public void gettrack(View view) {
        Intent intent = new Intent(this, trackAllActivity.class);
        startActivity(intent);
    }
    private class readThread extends Thread {

        public void run() {

            byte[] buffer = new byte[64];

            while (true) {
                Message msg = Message.obtain();
                if (!isOpen) {
                    break;
                }
                int length = MyApp.driver.ReadData(buffer, 64);
                if (length > 0) {
                    String recv = toHexString(buffer, length);
                    msg.obj = recv;
                    handler1.sendMessage(msg);
                }
            }
        }
    }

    /**
     * 将byte[]数组转化为String类型
     * @param arg
     *            需要转换的byte[]数组
     * @param length
     *            需要转换的数组长度
     * @return 转换后的String队形
     */
    private String toHexString(byte[] arg, int length) {
        String result = new String();
        if (arg != null) {
            for (int i = 0; i < length; i++) {
                result = result
                        + (Integer.toHexString(
                        arg[i] < 0 ? arg[i] + 256 : arg[i]).length() == 1 ? "0"
                        + Integer.toHexString(arg[i] < 0 ? arg[i] + 256
                        : arg[i])
                        : Integer.toHexString(arg[i] < 0 ? arg[i] + 256
                        : arg[i])) + " ";
            }
            return result;
        }
        return "";
    }

    /**
     * 将String转化为byte[]数组
     * @param arg
     *            需要转换的String对象
     * @return 转换后的byte[]数组
     */
    private byte[] toByteArray(String arg) {
        if (arg != null) {
            /* 1.先去除String中的' '，然后将String转换为char数组 */
            char[] NewArray = new char[1000];
            char[] array = arg.toCharArray();
            int length = 0;
            for (int i = 0; i < array.length; i++) {
                if (array[i] != ' ') {
                    NewArray[length] = array[i];
                    length++;
                }
            }
            /* 将char数组中的值转成一个实际的十进制数组 */
            int EvenLength = (length % 2 == 0) ? length : length + 1;
            if (EvenLength != 0) {
                int[] data = new int[EvenLength];
                data[EvenLength - 1] = 0;
                for (int i = 0; i < length; i++) {
                    if (NewArray[i] >= '0' && NewArray[i] <= '9') {
                        data[i] = NewArray[i] - '0';
                    } else if (NewArray[i] >= 'a' && NewArray[i] <= 'f') {
                        data[i] = NewArray[i] - 'a' + 10;
                    } else if (NewArray[i] >= 'A' && NewArray[i] <= 'F') {
                        data[i] = NewArray[i] - 'A' + 10;
                    }
                }
                /* 将 每个char的值每两个组成一个16进制数据 */
                byte[] byteArray = new byte[EvenLength / 2];
                for (int i = 0; i < EvenLength / 2; i++) {
                    byteArray[i] = (byte) (data[i * 2] * 16 + data[i * 2 + 1]);
                }
                return byteArray;
            }
        }
        return new byte[] {};
    }
    public class MyLocationListener extends BDAbstractLocationListener {
        @Override
        public void onReceiveLocation(BDLocation location) {
            //此处的BDLocation为定位结果信息类，通过它的各种get方法可获取定位相关的全部结果
            //以下只列举部分获取经纬度相关（常用）的结果信息
            //更多结果信息获取说明，请参照类参考中BDLocation类中的说明

            double latitude = location.getLatitude();    //获取纬度信息
            double longitude = location.getLongitude();    //获取经度信息
            float radius = location.getRadius();    //获取定位精度，默认值为0.0f
            float direction = location.getDirection();

            String errorCode = location.getCoorType();
            //获取定位类型、定位错误返回码，具体信息可参照类参考中BDLocation类中的说明

            Log.d("gps1", "第一次  " + latitude + "   " + longitude + "   " + errorCode);

            //封装
            gpsinfo thisgps = new gpsinfo();
            thisgps.setLatitude(latitude);
            thisgps.setLongitude(longitude);
            thisgps.setRadius(radius);
            thisgps.setDirection(direction);
            gpsdata.add(thisgps);

            JSONArray jsonArray = new JSONArray();
            JSONObject tmpObj = null;
            int count = gpsdata.size();
            for (int i = 0; i < count; i++) {
                tmpObj = new JSONObject();
                try {
                    tmpObj.put("latitude", gpsdata.get(i).getLatitude());
                    tmpObj.put("longitude", gpsdata.get(i).getLongitude());
                    tmpObj.put("radius", gpsdata.get(i).getRadius());
                    tmpObj.put("direction", gpsdata.get(i).getDirection());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                jsonArray.put(tmpObj);
                tmpObj = null;
            }
            thisGpsInfos = jsonArray.toString(); // 将JSONArray转换得到String
            Log.d("gps1", "personInfos    " + thisGpsInfos);

            if (nowsend) {
                if (mStart.getText().equals("断开连接")) {
                    gpsdata.clear();
                    tcpClient.sendMsg(thisGpsInfos);
                } else Toast.makeText(MainActivity.this, "Socket未连接", Toast.LENGTH_SHORT).show();
            }
        }
    }
}