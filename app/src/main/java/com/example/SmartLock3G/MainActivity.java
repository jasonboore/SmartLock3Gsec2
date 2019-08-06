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





import org.json.JSONArray;
import org.json.JSONException;
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
    String thisGpsInfos;
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

    public void sendGPS(View view) {
        if (mStart.getText().equals("断开连接")) {
            gpsdata.clear();
            tcpClient.sendMsg(thisGpsInfos);
        } else Toast.makeText(this, "Socket未连接", Toast.LENGTH_SHORT).show();
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

            //遍历解析
//            JSONArray jsonArray1 = null;
//            try {
//                jsonArray1 = new JSONArray(thisGpsInfos);
//                for (int i = 0; i < jsonArray1.length(); i++) {
//                    JSONObject jsonObj = jsonArray1.getJSONObject(i);
//                    Double latitude1 = jsonObj.getDouble("latitude");
//                    Double longitude1 = jsonObj.getDouble("longitude");
//                    Log.d("gps2", "第二次  " + latitude1 + "   " + longitude1 );
//                }
//            } catch (JSONException e) {
//                e.printStackTrace();
//            }
        }
        }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_tcp);
        InitView();
        InitDate();
        initLocation();
    }

    private void InitDate() {
        mLocationClient = new LocationClient(getApplicationContext());
        //声明LocationClient类
        mLocationClient.registerLocationListener(myListener);
        //注册监听函数
        gpsdata=new ArrayList<gpsinfo>();
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

                }
                else {
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
}