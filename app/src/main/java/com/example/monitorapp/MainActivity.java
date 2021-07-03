package com.example.monitorapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Geocoder;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.entity.pb.PoiResult;
import com.baidu.mapapi.CoordType;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.favorite.FavoriteManager;
import com.baidu.mapapi.favorite.FavoritePoiInfo;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.Dot;
import com.baidu.mapapi.map.DotOptions;
import com.baidu.mapapi.map.InfoWindow;
import com.baidu.mapapi.map.MapPoi;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.Marker;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.Overlay;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.map.Text;
import com.baidu.mapapi.map.TextOptions;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.geocode.GeoCodeOption;
import com.baidu.mapapi.search.geocode.GeoCodeResult;
import com.baidu.mapapi.search.geocode.GeoCoder;
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult;
import com.baidu.mapapi.utils.DistanceUtil;
import com.example.monitorapp.ui.dashboard.DashboardFragment;
import com.example.monitorapp.ui.home.HomeFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private Socket socket;
    String ss=null;
    private DataInputStream in = null;
    private MapView mMapView = null;
    private LatLng ll;//当前老人位置地理信息
    private double lat=0,lon=0;
    private BaiduMap baiduMap;
    private OverlayOptions option;
    private OverlayOptions clickOption;
    private LatLng ll_selected=null;//当前选择的地理信息
    private LatLng ll_selected_del=null;
    private List <LatLng> ll_track= new ArrayList<LatLng>();
    private List <Date> t_track=new ArrayList<Date>();
    private GeoCoder mCoder;//逆地址编码
    private int pointNum=0;
    private ArrayList<Map<String,Object>> list;
    private SQLiteDatabase db;//数据库
    private ArrayList<Map<String,Object>> ll_list;
    private ListView lv;
    private boolean isDisSafe=true;//常用距离和当前距离差距是否过大
    private boolean isTimeSafe=true;//当前时间和读取的数据时间的差距是否过大
    //防止重复通知
    private boolean isNoteDis=false;//是否已经通知过距离报警
    private boolean isNoteTime=false;//是否已经通知过时间报警
    private PowerManager.WakeLock wakeLock = null;
    private NotificationManager notificationManager;
    private String notificationId="serviced";
    private String notificationName="serviceName";
    public int freshTime=60;
    private FragmentManager fragmentManager;
    private FragmentTransaction fragmentTransaction;
    private Fragment homeFragment;
    private EditText et_city;
    private EditText et_address;
    //定时器类，定时查询定位
    Timer timer;
    TimerTask task;
    TextView tv1;
    //TextView tv2;
    TextView tvMore;
    InfoWindow ifw;
    Date t;
    private boolean isFirst=false;
    private Handler uiHandler = new Handler(){
        public void handleMessage(Message msg){
            switch (msg.what){
                case 1:
                    draw();
                    findAll();//显示当前常用位置，同时检查当前位置和常用位置的距离
                    checkTimeSafe();
                    if(!isDisSafe&&!isNoteDis){
                        showNotification(MainActivity.this,"请注意老人动向","老人离常用地点较远，请注意老人位置",getMainActivityIntent(MainActivity.this,"abc",1,"abc"));
                        isNoteDis=true;//最多通知一次
                    }
                    if(!isTimeSafe&&!isNoteTime){
                        showNotification(MainActivity.this,"长时间没有老人的位置数据","可能是老人关闭了app或者无网络，请注意",getMainActivityIntent(MainActivity.this,"abc",1,"abc"));
                        isNoteTime=true;//最多通知一次
                    }
                    break;
                case 2:
                    //draw_more();
                    //TextView tv=findViewById(R.id.tv);
                    //tv.setText(ss);
            }
        }
    };
    private void draw_more(){
        baiduMap.clear();
        OverlayOptions mOverlayOptions = new PolylineOptions()
                .width(3)
                .color(0xAAFF0000)
                .points(ll_track);
        Overlay mPolyline = baiduMap.addOverlay(mOverlayOptions);

    }
    private void checkTimeSafe(){
        long timeDis=System.currentTimeMillis()-t.getTime();
        isTimeSafe=true;
        if(timeDis>=600*1000){
            isTimeSafe=false;
        }
        else{
            isNoteTime=false;//此时时间不用报警，则将通知状态重置，说明可以通知了
        }
    }
    private static void showNotification(Context context, String contentTitle, String contentText, PendingIntent intent) {
        int channelId = new Random().nextInt(543254);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, String.valueOf(channelId));
        builder.setContentTitle(contentTitle)
                .setContentText(contentText)
                .setContentIntent(intent)
                .setTicker("")//通知首次出现在通知栏，带上升动画效果的
                .setPriority(Notification.PRIORITY_MAX)//设置该通知优先级
                .setAutoCancel(true)//设置这个标志当用户单击面板就可以让通知将自动取消
                .setOngoing(false)//ture，设置他为一个正在进行的通知。他们通常是用来表示一个后台任务,用户积极参与(如播放音乐)或以某种方式正在等待,因此占用设备(如一个文件下载,同步操作,主动网络连接)
                .setDefaults(Notification.DEFAULT_VIBRATE);//向通知添加声音、闪灯和振动效果的最简单、最一致的方式是使用当前的用户默认设置，使用defaults属性，可以组合：

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String name = "yourname";
            NotificationChannel mChannel = new NotificationChannel(String.valueOf(channelId), name, NotificationManager.IMPORTANCE_HIGH);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(mChannel);
                builder.setChannelId(String.valueOf(channelId));
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setSmallIcon(R.drawable.ic_launcher_foreground); //需要使用背景透明，图标为纯白色的图标。不然在很多机型上，如果直接使用应用icon,会直接显示纯白色图标，体验会不好。
        } else {
            builder.setSmallIcon(R.mipmap.ic_launcher);
        }
        if (notificationManager != null) {
            notificationManager.notify(channelId * 10, builder.build());
        }
    }
    //https://stackoverflow.com/questions/3168484/pendingintent-works-correctly-for-the-first-notification-but-incorrectly-for-the
    private static PendingIntent getMainActivityIntent(Context context, String jumpPageType, int requestCode, String sourceId) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setAction(Long.toString(System.currentTimeMillis()));
        intent.putExtra("jump_page_type", jumpPageType);
        intent.putExtra("source_id", sourceId);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(new ComponentName(context, MainActivity.class));
        // FLAG_ONE_SHOT:该PendingIntent只作用一次。在该PendingIntent对象通过send()方法触发过后，PendingIntent将自动调用cancel()进行销毁，那么如果你再调用send()方法的话，系统将会返回一个SendIntentException。
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_ONE_SHOT);
    }
    private void draw(){
        //tv1.setText(ss);
        baiduMap.clear();
        ll= new LatLng(lat,lon);
        BitmapDescriptor mBitmap = BitmapDescriptorFactory.fromResource(R.drawable.icon_gcoding);
        option = new MarkerOptions().position(ll).icon(mBitmap);
        baiduMap.addOverlay(option);
        baiduMap.setOnMarkerClickListener(new BaiduMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
                //Toast.makeText(MainActivity.this,df.format(t),Toast.LENGTH_LONG).show();
                Button btn = new Button(getApplicationContext());
                btn.setBackgroundResource(R.drawable.popup);
                btn.setText("老人当前位置:"+df.format(t));
                ifw = new InfoWindow(btn, ll, -50);
                baiduMap.showInfoWindow(ifw);
                return true;
            }
        });
        if(!isFirst) {
            MapStatusUpdate msu = MapStatusUpdateFactory.newLatLng(ll);
            baiduMap.animateMapStatus(msu);
            isFirst=true;
        }
    }
    /*public void showList(){
        SimpleAdapter listAdapter = new SimpleAdapter(this,ll_list,R.layout.list_item,new String[]{"_id","lat","lon"},new int[]{R.id.tv_id,R.id.tv_lat,R.id.tv_lon});
        lv=findViewById(R.id.lv);
        lv.setAdapter(listAdapter);
    }*/
    public void initDB(){
        MapDBOpenHelper dbHelper=new MapDBOpenHelper(this,MapDBOpenHelper.DATABASE_NAME,null,1);
        db=dbHelper.getWritableDatabase();
        ll_list=new ArrayList<>();
        findAll();
    }
    protected void findAll(){
        ll_list.clear();
        @SuppressLint("Recycle")Cursor cursor = db.rawQuery("select * from Map",null);
        Map<String,Object> item = new HashMap<>();
        item.put("_id","常用地点序号");
        item.put("lat","纬度");item.put("lon","经度");
        ll_list.add(item);
        cursor.moveToFirst();
        while(!cursor.isAfterLast()){
            String id=cursor.getString(0);
            String lat=cursor.getString(1);
            String lon=cursor.getString(2);
            item = new HashMap<>();
            item.put("_id",id);
            item.put("lat",lat);
            item.put("lon",lon);
            ll_list.add(item);
            cursor.moveToNext();
        }
        isDisSafe=false;
        for(int i=1;i<ll_list.size();i++){
            BitmapDescriptor bmap = BitmapDescriptorFactory.fromResource(R.drawable.icon_geo);
            item = ll_list.get(i);
            Double latitude=Double.parseDouble((String)item.get("lat"));
            Double longtitude=Double.parseDouble((String)item.get("lon"));
            LatLng llItem=new LatLng(latitude,longtitude);
            double dis = DistanceUtil.getDistance(llItem,ll);
            if(dis<10000){
                isDisSafe=true;
            }
            clickOption = new MarkerOptions().position(llItem).icon(bmap);
            baiduMap.addOverlay(clickOption);
        }
        //showList();
        if(isDisSafe==true){//此时在安全距离内，则通知开启
            isNoteDis=false;
        }
    }
    protected void dbAdd(LatLng ll){
        ContentValues values=new ContentValues();
        values.put("lat",Double.toString(ll.latitude).trim());
        values.put("lon",Double.toString(ll.longitude).trim());
        long lo=db.insert(MapDBOpenHelper.TABLE_NAME,null,values);
        if(lo==-1) Toast.makeText(getApplicationContext(),"数据插入失败！",Toast.LENGTH_SHORT).show();
    }
    protected void dbDel(LatLng ll){
        String sql="delete from Map where lat='"+Double.toString(ll.latitude).trim()+"' and lon='"+Double.toString(ll.longitude).trim()+"'";
        db.execSQL(sql);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //-----------------------------初始化下方导航栏----------------------------------------------
        BottomNavigationView navView = findViewById(R.id.nav_view);
        navView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                Intent intent;
                switch (item.getItemId()){
                    case R.id.navigation_dashboard:
                        intent = new Intent(MainActivity.this,MainActivity2.class);
                        startActivity(intent);
                        break;
                    case R.id.navigation_notifications:
                        intent = new Intent(MainActivity.this,MainActivity3.class);
                        startActivity(intent);
                        break;
                }
                return false;
            }
        });
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        /*AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(navView, navController);*/

        //-----------------------------------------------------------------------------------------
        mMapView = (MapView) findViewById(R.id.bmapView);
        tv1= findViewById(R.id.tv);
        tv1.setText("地图内容每"+Integer.toString(freshTime)+"秒刷新一次");
        //et_city=findViewById(R.id.et_city);
        //et_address=findViewById(R.id.et_address);
        Button btn1=findViewById(R.id.btn1);
        //Button btn_setPositon=findViewById(R.id.btn_setPosition);
        //Button btn_del=findViewById(R.id.btn_del);
        Button btn_show=findViewById(R.id.btn_show);
        Button btn_refresh=findViewById(R.id.btn_refresh);


        baiduMap=mMapView.getMap();
        initDB();
        //showList();
        //EditText监听
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {


            }

            @Override
            public void afterTextChanged(Editable editable) {

                    String city, add;
                    city = et_city.getText().toString().trim();
                    add = et_address.getText().toString().trim();
                    if (city.equals("") || add.equals("")) ;
                    else {
                        mCoder.geocode(new GeoCodeOption()
                                .city(et_city.getText().toString().trim())
                                .address(et_address.getText().toString().trim())
                        );
                    }
                }
        };
        //et_address.addTextChangedListener(watcher);
        //et_address.addTextChangedListener(watcher);

        //------------
        //权限申请
        List<String> permissionList = new ArrayList<>();
        if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED){
            permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED){
            permissionList.add(Manifest.permission.READ_PHONE_STATE);
        }
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if(!permissionList.isEmpty()) {
            String[] permissions = permissionList.toArray(new String[permissionList.size()]);
            ActivityCompat.requestPermissions(MainActivity.this, permissions, 1);
        }
        else{
            //----------------wakeLock使得进程即使在后台也不会被杀死-----------------------------------
            TimerTask task_keep = new TimerTask(){
                public void run(){
                    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK|PowerManager.ON_AFTER_RELEASE,MainActivity.class.getName());
                    wakeLock.acquire();
                }
            };
            Timer timer_keep=new Timer();
            timer_keep.schedule(task_keep,1000);
            //--------------------------------------------------------------------------------------
            //----------------------socket请求的线程，从服务器上读取数据------------------------------
            timer = new Timer();
            task = new TimerTask(){
                public void run(){
                    try{
                        socket = new Socket("8.140.20.196",8989);
                        System.out.println(socket.isConnected());
                    }catch (IOException e){
                        e.printStackTrace();
                    }
                    try {
                        in = new DataInputStream(socket.getInputStream());
                    }
                    catch (Exception e)
                    {
                        Log.d("connect error", "run: ");
                    }
                    byte[] reply=new byte[1024];
                    int len=-1;
                    try{
                        len=in.read(reply);

                    }catch(IOException e){
                        e.printStackTrace();
                    }
                    if(len<=0){
                        ss="没有数据";
                    }
                    else{
                        try{
                            ss= new String(reply,"utf-8").trim();
                            String[] sp=ss.split(" ");
                            lat= Double.parseDouble(sp[0]);
                            lon= Double.parseDouble(sp[1]);
                            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
                            try {
                                t=df.parse(sp[2]+" "+sp[3]);
                            }catch (Exception e){
                                e.printStackTrace();
                            }
                        }catch(UnsupportedEncodingException e){
                            e.printStackTrace();
                        }
                        Message msg=new Message();
                        msg.what=1;
                        uiHandler.sendMessage(msg);
                    }
                }
            };
            timer.schedule(task,2,1*freshTime*1000);
            //loc();
        }
        //---------------------------------地图点击事件----------------------------------------------
        BaiduMap.OnMapClickListener listener = new BaiduMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                baiduMap.clear();
                draw();
                //tv2.setText(Double.toString(latLng.latitude)+" "+Double.toString(latLng.longitude));
                BitmapDescriptor bmap = BitmapDescriptorFactory.fromResource(R.drawable.icon_geo);
                clickOption = new MarkerOptions().position(latLng).icon(bmap);
                baiduMap.addOverlay(clickOption);
                ll_selected=latLng;
            }
            @Override
            public void onMapPoiClick(MapPoi mapPoi) {
            }
        };
        baiduMap.setOnMapClickListener(listener);
        //------------------------------------------------------------------------------------------
        btn1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //showNotification(MainActivity.this,"请注意老人动向","老人离常用地点较远，请注意老人位置",getMainActivityIntent(MainActivity.this,"abc",1,"abc"));
                loc();
            }
        });


        btn_refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EditText et_refresh=findViewById(R.id.et_refresh);
                int num=Integer.parseInt(et_refresh.getText().toString().trim());
                freshTime=num;
                TextView tv=findViewById(R.id.tv);
                task.cancel();
                task = new TimerTask(){
                    public void run(){
                        try{
                            socket = new Socket("8.140.20.196",8989);
                            System.out.println(socket.isConnected());
                        }catch (IOException e){
                            e.printStackTrace();
                        }
                        try {
                            in = new DataInputStream(socket.getInputStream());
                        }
                        catch (Exception e)
                        {
                            Log.d("connect error", "run: ");
                        }
                        byte[] reply=new byte[1024];
                        int len=-1;
                        try{
                            len=in.read(reply);

                        }catch(IOException e){
                            e.printStackTrace();
                        }
                        if(len<=0){
                            ss="没有数据";
                        }
                        else{
                            try{
                                ss= new String(reply,"utf-8").trim();
                                String[] sp=ss.split(" ");
                                lat= Double.parseDouble(sp[0]);
                                lon= Double.parseDouble(sp[1]);
                                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
                                try {
                                    t=df.parse(sp[2]+" "+sp[3]);
                                }catch (Exception e){
                                    e.printStackTrace();
                                }
                            }catch(UnsupportedEncodingException e){
                                e.printStackTrace();
                            }
                            Message msg=new Message();
                            msg.what=1;
                            uiHandler.sendMessage(msg);
                        }
                    }
                };
                timer.schedule(task,2,1*freshTime*1000);
                tv.setText("地图内容每"+Integer.toString(freshTime).trim()+"秒刷新一次");
            }
        });
        btn_show.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try{
                            socket = new Socket("8.140.20.196",8089);
                            System.out.println(socket.isConnected());
                        }catch (IOException e){
                            e.printStackTrace();
                        }
                        try {
                            in = new DataInputStream(socket.getInputStream());
                        }catch (Exception e)
                        {
                            Log.d("connect error", "run: ");
                        }
                        byte[] reply=new byte[1024];
                        int len=-1;
                        try{
                            len=in.read(reply);

                        }catch(IOException e){
                            e.printStackTrace();
                        }
                        if(len<=0){
                            ss="没有数据";
                        }
                        else {
                            try {
                                ll_track.clear();
                                t_track.clear();
                                ss= new String(reply,"utf-8").trim();

                                String [] sp1=ss.split("/");
                                ss=sp1[0];
                                LatLng new_ll;
                                Date new_t;
                                for(int i=0;i<5;i++){

                                    String [] sp2 = sp1[i].split(" ");
                                    new_ll = new LatLng(Double.parseDouble(sp2[0]),Double.parseDouble(sp2[1]));
                                    ll_track.add(new_ll);
                                    //System.out.print(Double.toString(ll.latitude)+Double.toString(ll.longitude));
                                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
                                    try {
                                        new_t=df.parse(sp2[2]+" "+sp2[3]);
                                        t_track.add(t);
                                    }catch (Exception e){
                                        e.printStackTrace();
                                    }
                                }

                            }catch (Exception e){
                                e.printStackTrace();
                            }
                            baiduMap.clear();
                            BitmapDescriptor mBitmap = BitmapDescriptorFactory.fromResource(R.drawable.icon_st);
                            option = new MarkerOptions().position(ll_track.get(4)).icon(mBitmap);
                            baiduMap.addOverlay(option);
                            BitmapDescriptor mBitmap2 = BitmapDescriptorFactory.fromResource(R.drawable.icon_en);
                            option = new MarkerOptions().position(ll_track.get(0)).icon(mBitmap2);
                            baiduMap.addOverlay(option);
                            OverlayOptions mOverlayOptions = new PolylineOptions()
                                    .width(3)
                                    .color(0xAAFF0000)
                                    .points(ll_track);
                            Overlay mPolyline = baiduMap.addOverlay(mOverlayOptions);
                            Message msg=new Message();
                            msg.what=2;
                            uiHandler.sendMessage(msg);
                        }
                    }
                }).start();
            }
        });
    }
    protected void onResume() {
        super.onResume();
        //在activity执行onResume时执行mMapView. onResume ()，实现地图生命周期管理
        mMapView.onResume();
    }
    protected void onPause() {
        super.onPause();
        //在activity执行onPause时执行mMapView. onPause ()，实现地图生命周期管理
        mMapView.onPause();
    }
    protected void onDestroy() {
        super.onDestroy();
        //在activity执行onDestroy时执行mMapView.onDestroy()，实现地图生命周期管理
        mMapView.onDestroy();
    }

    public void loc(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                //double lat=0,lon=0;
                try{
                    socket = new Socket("8.140.20.196",8989);
                    System.out.println(socket.isConnected());
                }catch (IOException e){
                    e.printStackTrace();
                }
                try {
                    in = new DataInputStream(socket.getInputStream());
                }
                catch (Exception e)
                {
                    Log.d("connect error", "run: ");
                }
                byte[] reply=new byte[1024];
                int len=-1;
                try{
                    len=in.read(reply);

                }catch(IOException e){
                    e.printStackTrace();
                }
                if(len<=0){
                    ss="没有数据";
                }
                else{
                    try{
                        ss= new String(reply,"utf-8").trim();
                        String[] sp=ss.split(" ");
                        lat= Double.parseDouble(sp[0]);
                        lon= Double.parseDouble(sp[1]);
                        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
                        try {
                            t=df.parse(sp[2]+" "+sp[3]);
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    }catch(UnsupportedEncodingException e){
                        e.printStackTrace();
                    }
                }
            }
        }).start();
        //tv1.setText(ss);
        baiduMap.clear();
        ll= new LatLng(lat,lon);
        BitmapDescriptor mBitmap = BitmapDescriptorFactory.fromResource(R.drawable.icon_gcoding);
                    /*InfoWindow.OnInfoWindowClickListener listener = new InfoWindow.OnInfoWindowClickListener() {
                        @Override
                        public void onInfoWindowClick() {
                            Toast.makeText(MainActivity.this,"yes",Toast.LENGTH_LONG).show();
                        }
                    };
                    ifw = new InfoWindow(mBitmap,ll,-100,listener);*/
        //baiduMap.showInfoWindow(ifw);
        OverlayOptions option = new MarkerOptions().position(ll).icon(mBitmap);
        baiduMap.addOverlay(option);
        baiduMap.setOnMarkerClickListener(new BaiduMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
                //Toast.makeText(MainActivity.this,df.format(t),Toast.LENGTH_LONG).show();
                Button btn = new Button(getApplicationContext());
                btn.setBackgroundResource(R.drawable.popup);
                btn.setText(df.format(t));
                ifw = new InfoWindow(btn, ll, -50);
                baiduMap.showInfoWindow(ifw);
                return true;
            }
        });
        MapStatusUpdate msu = MapStatusUpdateFactory.newLatLng(ll);
        baiduMap.animateMapStatus(msu);
        findAll();
    }
}