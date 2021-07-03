package com.example.monitorapp;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.navigation.Navigation;

import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.InfoWindow;
import com.baidu.mapapi.map.MapPoi;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.Marker;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.geocode.GeoCodeOption;
import com.baidu.mapapi.search.geocode.GeoCodeResult;
import com.baidu.mapapi.search.geocode.GeoCoder;
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult;
import com.baidu.mapapi.utils.DistanceUtil;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.DataInputStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity2 extends AppCompatActivity {
    String ss=null;
    private DataInputStream in = null;
    private MapView mMapView = null;
    private LatLng ll;//当前老人位置地理信息
    private double lat=30.233176,lon=120.047919;
    private BaiduMap baiduMap;
    private OverlayOptions option;
    private OverlayOptions clickOption;
    private LatLng ll_selected=null;//当前选择的地理信息
    private LatLng ll_selected_del=null;
    private GeoCoder mCoder;//逆地址编码
    private int pointNum=0;
    private ArrayList<Map<String,Object>> list;
    private SQLiteDatabase db;//数据库
    private ArrayList<Map<String,Object>> ll_list;
    private ListView lv;
    private SearchResult mSearch;

    private PowerManager.WakeLock wakeLock = null;
    private NotificationManager notificationManager;
    private String notificationId="serviced";
    private String notificationName="serviceName";
    private int freshTime=60;
    private FragmentManager fragmentManager;
    private FragmentTransaction fragmentTransaction;
    private Fragment homeFragment;
    private EditText et_city;
    private EditText et_address;
    private String address;
    //定时器类，定时查询定位
    Timer timer;
    TimerTask task;
    TextView tv1;
    //TextView tv2;
    TextView tvMore;
    InfoWindow ifw;
    Date t;
    private boolean isFirst=false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        //-----------------------------------------------------------------------------------------
        BottomNavigationView navView = findViewById(R.id.nav_view);
        navView.setSelectedItemId(navView.getMenu().getItem(1).getItemId());

        navView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                Intent intent;
                switch (item.getItemId()){
                    case R.id.navigation_home:
                        intent = new Intent(MainActivity2.this,MainActivity.class);
                        startActivity(intent);
                        break;
                    case R.id.navigation_notifications:
                        intent = new Intent(MainActivity2.this,MainActivity3.class);
                        startActivity(intent);
                }
                return false;
            }
        });
        //-----------------------------------------------------------------------------------------


        mMapView=findViewById(R.id.bmapView);
        baiduMap=mMapView.getMap();
        et_city=findViewById(R.id.et_city);
        et_address=findViewById(R.id.et_address);
        Button btn_setPositon = findViewById(R.id.btn_setPosition);
        Button btn_del = findViewById(R.id.btn_del);
        lv = findViewById(R.id.lv);
        initGeoCoder();
        initDB();
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
        et_address.addTextChangedListener(watcher);
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
        btn_setPositon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(ll_selected==null){
                    Toast.makeText(getApplicationContext(),"请在地图上选中一个目标",Toast.LENGTH_LONG);
                    return;
                }
                dbAdd(ll_selected);
                draw();
                findAll();
                ll_selected=null;
                //tv2.setText(Integer.toString(pointNum));
            }
        });
        btn_del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(ll_selected_del==null){
                    Toast.makeText(getApplicationContext(),"请在常用地点序号中选择一个序号",Toast.LENGTH_LONG);
                    return;
                }
                if(ll_list.size()<=2){
                    Toast.makeText(getApplicationContext(),"至少要有一个常用地点",Toast.LENGTH_LONG);
                    return;
                }
                dbDel(ll_selected_del);
                draw();
                findAll();
                ll_selected_del=null;
            }
        });
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if(i==0)return;
                Map<String,Object> listItem=(Map<String,Object>)lv.getItemAtPosition(i);
                Double lat,lon;
                lat=Double.parseDouble((String)listItem.get("lat"));
                lon=Double.parseDouble((String)listItem.get("lon"));
                LatLng llTemp=new LatLng(lat,lon);
                ll_selected_del=llTemp;
                MapStatusUpdate msu = MapStatusUpdateFactory.newLatLng(llTemp);
                baiduMap.animateMapStatus(msu);
                findAll();
            }
        });
    }
    private void draw(){

        baiduMap.clear();
        ll= new LatLng(lat,lon);
        /*BitmapDescriptor mBitmap = BitmapDescriptorFactory.fromResource(R.drawable.icon_gcoding);
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
        });*/
        if(!isFirst) {
            MapStatusUpdate msu = MapStatusUpdateFactory.newLatLng(ll);
            baiduMap.animateMapStatus(msu);
            isFirst=true;
        }
    }
    public void initGeoCoder(){
        OnGetGeoCoderResultListener listenerG = new OnGetGeoCoderResultListener() {
            @Override
            public void onGetGeoCodeResult(GeoCodeResult geoCodeResult) {
                if (null != geoCodeResult && null != geoCodeResult.getLocation()) {
                    if (geoCodeResult == null || geoCodeResult.error != SearchResult.ERRORNO.NO_ERROR) {
                        //没有检索到结果
                        //tv2.setText("没有检索到结果");
                        return;
                    } else {
                        baiduMap.clear();
                        draw();
                        double latitude = geoCodeResult.getLocation().latitude;
                        double longitude = geoCodeResult.getLocation().longitude;
                        LatLng ll = new LatLng(latitude,longitude);
                        //tv2.setText(Double.toString(ll.latitude)+" "+Double.toString(ll.longitude));
                        BitmapDescriptor bmap = BitmapDescriptorFactory.fromResource(R.drawable.icon_geo);
                        clickOption = new MarkerOptions().position(ll).icon(bmap);
                        baiduMap.addOverlay(clickOption);
                        ll_selected=ll;
                        MapStatusUpdate msu = MapStatusUpdateFactory.newLatLng(ll_selected);
                        baiduMap.animateMapStatus(msu);
                    }
                }
            }

            @Override
            public void onGetReverseGeoCodeResult(ReverseGeoCodeResult reverseGeoCodeResult) {
                if (reverseGeoCodeResult == null || reverseGeoCodeResult.error != SearchResult.ERRORNO.NO_ERROR) {
                    //没有找到检索结果
                    return;
                } else {
                    //详细地址
                    address = reverseGeoCodeResult.getAddress();
                    //行政区号
                    int adCode = reverseGeoCodeResult. getCityCode();
                }
            }
        };
        mCoder = GeoCoder.newInstance();
        mCoder.setOnGetGeoCodeResultListener(listenerG);
    }
    public void showList(){
        SimpleAdapter listAdapter = new SimpleAdapter(this,ll_list,R.layout.list_item,new String[]{"_id","lat","lon","add"},new int[]{R.id.tv_id,R.id.tv_lat,R.id.tv_lon,R.id.tv_add});
        lv=findViewById(R.id.lv);
        lv.setAdapter(listAdapter);
    }
    public void initDB(){
        MapDBOpenHelper dbHelper=new MapDBOpenHelper(this,MapDBOpenHelper.DATABASE_NAME,null,1);

        db=dbHelper.getWritableDatabase();
        //dbHelper.onUpgrade(db,1,2);
        ll_list=new ArrayList<>();
        findAll();
    }
    protected void findAll(){
        baiduMap.clear();
        ll_list.clear();
        @SuppressLint("Recycle") Cursor cursor = db.rawQuery("select * from Map",null);
        Map<String,Object> item = new HashMap<>();
        item.put("_id","常用地点序号");
        item.put("lat","纬度");item.put("lon","经度");
        item.put("add","地点");
        ll_list.add(item);
        cursor.moveToFirst();
        while(!cursor.isAfterLast()){
            String id=cursor.getString(0);
            String lat=cursor.getString(1);
            String lon=cursor.getString(2);
            String add=cursor.getString(3);
            Double latitude=Double.parseDouble(lat);
            Double longtitude=Double.parseDouble(lon);
            LatLng ll=new LatLng(latitude,longtitude);
            item = new HashMap<>();
            item.put("_id",id);
            item.put("lat",lat);
            item.put("lon",lon);
            item.put("add",add);
            ll_list.add(item);
            cursor.moveToNext();
        }
        for(int i=1;i<ll_list.size();i++){
            BitmapDescriptor bmap = BitmapDescriptorFactory.fromResource(R.drawable.icon_geo);
            item = ll_list.get(i);
            Double latitude=Double.parseDouble((String)item.get("lat"));
            Double longtitude=Double.parseDouble((String)item.get("lon"));
            LatLng llItem=new LatLng(latitude,longtitude);
            clickOption = new MarkerOptions().position(llItem).icon(bmap);
            baiduMap.addOverlay(clickOption);
        }
        showList();
    }
    protected void dbAdd(LatLng ll){
        ContentValues values=new ContentValues();
        values.put("lat",Double.toString(ll.latitude).trim());
        values.put("lon",Double.toString(ll.longitude).trim());
        mCoder.reverseGeoCode(new ReverseGeoCodeOption()
                .location(ll)
                // 设置是否返回新数据 默认值0不返回，1返回
                .newVersion(1)
                // POI召回半径，允许设置区间为0-1000米，超过1000米按1000米召回。默认值为1000
                .radius(300));
        values.put("addre",address);
        long lo=db.insert(MapDBOpenHelper.TABLE_NAME,null,values);
        if(lo==-1) Toast.makeText(getApplicationContext(),"数据插入失败！",Toast.LENGTH_SHORT).show();
    }
    protected void dbDel(LatLng ll){
        String sql="delete from Map where lat='"+Double.toString(ll.latitude).trim()+"' and lon='"+Double.toString(ll.longitude).trim()+"'";
        db.execSQL(sql);
    }

}
