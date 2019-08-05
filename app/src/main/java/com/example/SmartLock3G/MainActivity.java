package com.example.SmartLock3G;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.example.SmartLock3G.Track.trackAllActivity;
import com.example.SmartLock3G.tools.gpsinfo;
import com.example.SmartLock3G.utils.DateUtil;
import com.example.SmartLock3G.utils.Pref;
import com.lpoint.tcpsocketlib.TcpClient;
import com.lpoint.tcpsocketlib.TcpSocketListener;


import net.sf.json.JSONArray;


import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    public LocationClient mLocationClient = null;
    private MyLocationListener myListener = new MyLocationListener();
    public Button bt_send, mStart;
    TcpClient tcpClient;
    private TextView tv_content;
    private EditText ed_send_text, EDIP, EDPORT;
    private String ThisPhoneIP = "";
    private  List<gpsinfo> gpsdata;
    private Pref sp;

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
            }

        }
    };
    public class MyLocationListener extends BDAbstractLocationListener {
        @Override
        public void onReceiveLocation(BDLocation location) {
            //此处的BDLocation为定位结果信息类，通过它的各种get方法可获取定位相关的全部结果
            //以下只列举部分获取经纬度相关（常用）的结果信息
            //更多结果信息获取说明，请参照类参考中BDLocation类中的说明

            double latitude = location.getLatitude();    //获取纬度信息
            double longitude = location.getLongitude();    //获取经度信息
            float radius = location.getRadius();    //获取定位精度，默认值为0.0f

            String coorType = location.getCoorType();
            //获取经纬度坐标类型，以LocationClientOption中设置过的坐标类型为准

            int errorCode = location.getLocType();
            //获取定位类型、定位错误返回码，具体信息可参照类参考中BDLocation类中的说明
            Log.d("gps1", "第一次  " + latitude + "   " + longitude + "   " + errorCode);

            gpsinfo thisgps = new gpsinfo();
            thisgps.setLatitude(latitude);
            thisgps.setLongitude(longitude);
            gpsdata.add(thisgps);


//            Map map = new HashMap();
//            map.put("Latitude", latitude);
//            map.put("longitude", longitude);
//            JSONObject jsonObject = new JSONObject(map);
//            String jsonString = jsonObject.toString();

//            gpsdata.put("time", SystemClock.currentThreadTimeMillis());
//            gpsdata.put("data",jsonString);

//            String jsonString1 = jsonObject1.toString();
//
//            Log.d("gps1", "jsonString1    "+jsonString1);

            if (!gpsdata.isEmpty()) {
                net.sf.json.JSONArray jsonArray = net.sf.json.JSONArray.fromObject(thisgps);
                String data1 = jsonArray.toString();
                Log.d("gps1", "这里的data1    " + data1);

                net.sf.json.JSONArray json = net.sf.json.JSONArray.fromObject(data1);

                if (json.size() > 0) {
                    for (int i = 0; i < json.size(); i++) {
                        // 遍历 jsonarray 数组，把每一个对象转成 json 对象
                        net.sf.json.JSONObject jsonObj = json.getJSONObject(i);
                        Log.d("gps1", "这里的解析的    " + jsonObj);
                    }
                }

//            try {
//                jsonArray = new JSONArray(jsonString1);
//                for (int i = 0; i < jsonArray.length(); i++) {
//                    JSONObject jsonObj = jsonArray.getJSONObject(i);
////                    double latitude1 = jsonObj.getDouble("latitude");
////                    double longitude1 = jsonObj.getDouble("longitude");
//                    String data1= jsonObj.getString("data");
//                    Log.d("gps1","第二次  "+data1);
//                }
//            } catch (JSONException e) {
//                e.printStackTrace();
//            }
            }
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLocationClient = new LocationClient(getApplicationContext());
        //声明LocationClient类
        mLocationClient.registerLocationListener(myListener);
        //注册监听函数
        gpsdata=new ArrayList<gpsinfo>();

        setContentView(R.layout.activity_main_tcp);
        InitView();
        ThisPhoneIP = getLocalIpAddress();  //获取本机IP
        initLocation();
    }

    private void initLocation() {

        LocationClientOption option = new LocationClientOption();

        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
//可选，设置定位模式，默认高精度
//LocationMode.Hight_Accuracy：高精度；
//LocationMode. Battery_Saving：低功耗；
//LocationMode. Device_Sensors：仅使用设备；

        option.setCoorType("bd09ll");
//可选，设置返回经纬度坐标类型，默认GCJ02
//GCJ02：国测局坐标；
//BD09ll：百度经纬度坐标；
//BD09：百度墨卡托坐标；
//海外地区定位，无需设置坐标类型，统一返回WGS84类型坐标

        option.setScanSpan(1000);
//可选，设置发起定位请求的间隔，int类型，单位ms
//如果设置为0，则代表单次定位，即仅定位一次，默认为0
//如果设置非0，需设置1000ms以上才有效

        option.setOpenGps(true);
//可选，设置是否使用gps，默认false
//使用高精度和仅用设备两种定位模式的，参数必须设置为true

        option.setLocationNotify(true);
//可选，设置是否当GPS有效时按照1S/1次频率输出GPS结果，默认false

        option.setIgnoreKillProcess(false);
//可选，定位SDK内部是一个service，并放到了独立进程。
//设置是否在stop的时候杀死这个进程，默认（建议）不杀死，即setIgnoreKillProcess(true)

        option.SetIgnoreCacheException(false);
//可选，设置是否收集Crash信息，默认收集，即参数为false

        option.setWifiCacheTimeOut(5*60*1000);
//可选，V7.2版本新增能力
//如果设置了该接口，首次启动定位时，会先判断当前Wi-Fi是否超出有效期，若超出有效期，会先重新扫描Wi-Fi，然后定位

        option.setEnableSimulateGps(false);
//可选，设置是否需要过滤GPS仿真结果，默认需要，即参数为false

        mLocationClient.setLocOption(option);
//mLocationClient为第二步初始化过的LocationClient对象
//需将配置好的LocationClientOption对象，通过setLocOption方法传递给LocationClient对象使用
//更多LocationClientOption的配置，请参照类参考中LocationClientOption类的详细说明
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
        mStart.setOnClickListener(this);
        sp = Pref.getInstance(this);
        EDIP.setText(sp.getHost());
        ed_send_text.setText(sp.getIdCode());
        EDPORT.setText(String.valueOf(sp.getPort()));

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
                Message msg = new Message();
                msg.what = 1;
                handler.sendMessage(msg);
                Log.d("wifi", "发起TCP连接时报出的异常   ");
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

                Message msg = new Message();
                msg.what = 1;
                handler.sendMessage(msg);
                Log.d("wifi", "发送消息时遇到异常，从这里抛出 ");
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
}