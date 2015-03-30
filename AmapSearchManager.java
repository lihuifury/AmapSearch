package com.handsmap.nsstour.manager.search;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.os.Message;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.location.LocationManagerProxy;
import com.amap.api.location.LocationProviderProxy;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.poisearch.PoiItemDetail;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;
import com.amap.api.services.route.BusRouteResult;
import com.amap.api.services.route.DriveRouteResult;
import com.amap.api.services.route.RouteSearch;
import com.amap.api.services.route.WalkRouteResult;
import com.handsmap.nsstour.config.Appconfig;
import com.handsmap.nsstour.manager.BaseManager;
import com.handsmap.util.extend.app.ToastUtil;

/**
 * Created by DaHui on 2015/2/4.
 * <p/>
 * Amap搜索管理
 * 功能包括：公交搜索，周边搜索等功能
 */
public class AmapSearchManager extends BaseManager implements RouteSearch.OnRouteSearchListener, AMapLocationListener, PoiSearch.OnPoiSearchListener
{
    private static AmapSearchManager amapSearchManager = null;
    //当前定位类型
    private int currentType = 1; //1表示搜索路径，2表示搜素POI
    private Context mContext;
    //接口抛数据到activity
    private AmapSearchListener amapSearchListener;
    //定位
    private LocationManagerProxy mLocationManagerProxy;
    //POI搜索
    private PoiSearch.Query query;
    private PoiSearch poiSearch;
    private PoiResult poiResult;
    //公交线路查询
    private RouteSearch.BusRouteQuery busRouteQuery;
    private RouteSearch routeSearch;
    //搜索条件
    private String keyWord = "酒店"; //搜索关键字
    private String searchType = "酒店"; //搜索类型
    private int searchRadius = 2000; //搜索半径
    private int poiSearchCurrentPage = 0; //搜索当前页码
    //没有下一页数据
    public static final int NO_NEXT_DATA_TYPE = 1;
    //没有上一页数据
    public static final int NO_PRE_DATA_TYPE = 2;
    //分页大小，默认10
    private int pageSize = 10;

    //单一实例
    public static AmapSearchManager getInstance(Context context)
    {
        if (amapSearchManager == null)
        {
            amapSearchManager = new AmapSearchManager(context);
        }
        return amapSearchManager;
    }

    /**
     * 私有构造函数
     *
     * @param context
     */
    private AmapSearchManager(Context context)
    {
        mContext = context;
        amapSearchListener = (AmapSearchListener) context;
        mLocationManagerProxy = LocationManagerProxy.getInstance(mContext);
    }

    /**
     * 开始搜索公交线路
     */
    public void startBusRouteQuery()
    {
        //搜索公交线路
        routeSearch = new RouteSearch(mContext);
        routeSearch.setRouteSearchListener(this);
        currentType = 1;
        //时间间隔设置为-1则只会定位一次
        mLocationManagerProxy.requestLocationData(LocationProviderProxy.AMapNetwork, -1, 15, this);
        mLocationManagerProxy.setGpsEnable(true);
    }

    /**
     * 开始搜索周边，对外方法
     */
    public void startAroundQuery(String key, String type, int radius, int pageSize)
    {
        this.keyWord = key;
        this.searchType = type;
        this.searchRadius = radius;
        this.pageSize = pageSize;
        this.poiSearchCurrentPage = 0; //还原搜索页码
        //搜索周边
        currentType = 2;
        //时间间隔设置为-1则只会定位一次
        mLocationManagerProxy.requestLocationData(LocationProviderProxy.AMapNetwork, -1, 15, this);
        mLocationManagerProxy.setGpsEnable(true);
    }

    /**
     * 搜下一页
     */
    public void nextAroundQuery()
    {
        if (query != null && poiSearch != null && poiResult != null)
        {
            if (poiResult.getPageCount() - 1 > poiSearchCurrentPage)
            {
                query.setPageNum(++poiSearchCurrentPage);
                poiSearch.searchPOIAsyn(); //搜去吧
            } else
            {
                //没有下一页
                handler.sendEmptyMessage(NO_NEXT_DATA_TYPE);
            }
        }
    }

    /**
     * 搜上一页
     */
    public void preAroundQuery()
    {
        if (query != null && poiSearch != null && poiResult != null)
        {
            if (poiSearchCurrentPage > 0)
            {
                query.setPageNum(--poiSearchCurrentPage);
                poiSearch.searchPOIAsyn(); //搜去吧
            } else
            {
                //没有上一页已经是第一页了
                handler.sendEmptyMessage(NO_PRE_DATA_TYPE);
            }
        }
    }

    /**
     * 处理没有数据的情况
     */
    android.os.Handler handler = new android.os.Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
            super.handleMessage(msg);
            amapSearchListener.searchNoData(msg.what); //没有搜索到数据
        }
    };

    /**
     * 搜索周边
     *
     * @param centerPoint 中心点
     */
    private void aroundSearch(LatLonPoint centerPoint)
    {
        // 第一个参数表示搜索字符串，第二个参数表示poi搜索类型，第三个参数表示poi搜索区域（空字符串代表全国）
        query = new PoiSearch.Query(keyWord, searchType, "025");
        //所有POI
        query.setLimitDiscount(false);
        query.setLimitGroupbuy(false);
        query.setPageNum(poiSearchCurrentPage);
        query.setPageSize(pageSize);
        poiSearch = new PoiSearch(mContext, query);
        poiSearch.setOnPoiSearchListener(this);
        poiSearch.setBound(new PoiSearch.SearchBound(centerPoint, searchRadius, true));//设置搜索区域为以lp点为圆心，其周围2000米范围
        poiSearch.searchPOIAsyn(); //搜去吧，异步搜索，回调接收
    }

    /**
     * 搜索公交线路
     *
     * @param startPoint 起始点,由定位获得
     */
    private void busRouteSearch(LatLonPoint startPoint)
    {
        //起点和终点信息
        RouteSearch.FromAndTo fromAndTo = new RouteSearch.FromAndTo(startPoint, Appconfig.BUS_END_POINT);
        //第一个参数表示路径规划的起点和终点，第二个参数表示公交查询模式，第三个参数表示公交查询城市区号，第四个参数表示是否计算夜班车，0表示不计算
        busRouteQuery = new RouteSearch.BusRouteQuery(fromAndTo, RouteSearch.BusDefault, "025", 0);
        routeSearch.calculateBusRouteAsyn(busRouteQuery);  //搜索公交线路
    }

    @Override
    public void onBusRouteSearched(BusRouteResult busRouteResult, int resultCode)
    {
        //公交线路查询返回
        amapSearchListener.busRouteSearched(busRouteResult, resultCode);
    }

    @Override
    public void onDriveRouteSearched(DriveRouteResult driveRouteResult, int i)
    {
        //驾车线路查询返回
    }

    @Override
    public void onWalkRouteSearched(WalkRouteResult walkRouteResult, int i)
    {
        //步行
    }

    @Override
    public void onPoiSearched(PoiResult poiResult, int i)
    {
        //POI搜索回调
        this.poiResult = poiResult;
        amapSearchListener.poiSearched(poiResult, i);
    }

    @Override
    public void onPoiItemDetailSearched(PoiItemDetail poiItemDetail, int i)
    {
        //POI详情搜索回调
    }

    @Override
    public void onLocationChanged(AMapLocation aMapLocation)
    {
        if (aMapLocation == null)
        {
            ToastUtil.showToastShort(mContext, "获取位置失败");
            return;
        }
        //位置改变回调方法
        if (currentType == 1)
        {
            //如果是线路搜索类型定位
            busRouteSearch(new LatLonPoint(aMapLocation.getLatitude(), aMapLocation.getLongitude()));
        } else if (currentType == 2)
        {
            //如果是Poi搜索类型定位
            aroundSearch(new LatLonPoint(aMapLocation.getLatitude(), aMapLocation.getLongitude()));
        }
    }

    @Override
    public void onLocationChanged(Location location)
    {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras)
    {

    }

    @Override
    public void onProviderEnabled(String provider)
    {

    }

    @Override
    public void onProviderDisabled(String provider)
    {

    }


    @Override
    public void recycleManager()
    {
        mLocationManagerProxy.destroy();
        routeSearch = null;
        busRouteQuery = null;
        amapSearchListener = null;
        amapSearchManager = null;
        super.recycleManager();
    }

    /**
     * 搜索结果接口
     *
     * @author DaHui
     */
    public interface AmapSearchListener
    {
        /**
         * 公交线路搜索
         *
         * @param busRouteResult 搜索结果
         * @param resultCode     状态码，不等于0就表示出错
         */
        public void busRouteSearched(BusRouteResult busRouteResult, int resultCode);

        /**
         * POI搜索回调
         *
         * @param result     搜索结果
         * @param resultCode 状态码，不等于0就表示出错
         */
        public void poiSearched(PoiResult result, int resultCode);

        /**
         * 未搜索到结果
         *
         * @param type POI搜索没有上一页或者下一页，NO_NEXT_DATA_TYPE和NO_PRE_DATA_TYPE
         */
        public void searchNoData(int type);

    }
}
