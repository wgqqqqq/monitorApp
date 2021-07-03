package com.example.monitorapp;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

public class MapDBOpenHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME= "mydb";//库名
    public static final String TABLE_NAME= "Map";  //表名
    public static final int DATABASE_VERSION=1;
    public static final int FRIENDS= 1;
    public static final int FRIENDS_ID=2;

    public static final String ID="_id";
    public static final String LAT="lat";
    public static final String LON="lon";
    public static final String ADD="addre";

    public MapDBOpenHelper(@Nullable Context context, @Nullable String name, @Nullable SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
    }


    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL("CREATE TABLE "+TABLE_NAME+"(_id integer primary key autoincrement,"+"lat varchar(20),lon varchar(20),addre varchar(50)"+")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        sqLiteDatabase.execSQL("DROP TABLE "+ TABLE_NAME);
        onCreate(sqLiteDatabase);
    }
}
