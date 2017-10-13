package com.mrsgx.campustalk.mvp.Main

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.annotation.RequiresApi
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import android.support.v4.view.ViewPager
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.TranslateAnimation
import android.widget.*
import com.mrsgx.campustalk.R
import com.mrsgx.campustalk.adapter.FragAdapter
import com.mrsgx.campustalk.data.GlobalVar
import com.mrsgx.campustalk.data.GlobalVar.Companion.RECONNECT_INTERVAL
import com.mrsgx.campustalk.data.Local.DB
import com.mrsgx.campustalk.data.Remote.WorkerRemoteDataSource
import com.mrsgx.campustalk.data.WorkerRepository
import com.mrsgx.campustalk.interfaces.NetEventManager
import com.mrsgx.campustalk.mvp.Profile.ProfileActivity
import com.mrsgx.campustalk.obj.CTUser
import com.mrsgx.campustalk.service.CTConnection
import com.mrsgx.campustalk.service.ConnService
import com.mrsgx.campustalk.service.NetStateListening
import com.mrsgx.campustalk.utils.SharedHelper
import com.mrsgx.campustalk.utils.TalkerProgressHelper
import com.mrsgx.campustalk.utils.Utils
import com.mrsgx.campustalk.widget.CTNote
import com.mrsgx.campustalk.widget.CTProfileCard
import com.mrsgx.campustalk.widget.MainViewPagerTransform
import com.zsoft.signala.ConnectionState
import kotlinx.android.synthetic.main.activity_main.*

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class MainActivity : FragmentActivity(), MainContract.View, NetStateListening.NetEvent, MatchFragment.OnFragmentInteractionListener
        , FollowFragment.OnFragmentInteractionListener, FindFragment.OnFragmentInteractionListener, SettingFragment.OnFragmentInteractionListener {


    override fun setNavigator(state: Int) {
        frg_navibar.visibility=state
    }


    override fun initFollowData(list: ArrayList<CTUser>) {
        if (mFollowFrag != null) {
            mFollowFrag!!.initData(list)
        }
    }

    override fun cancelFollow(uid: String) {
        mainpresenter.cancelFollow(uid)
    }

    override fun updateFollowList() {
        mainpresenter.updateFollowList()
    }

    override fun uploadImg(path: String, uid: String) {
        mainpresenter.uploadHeadpic(path, uid)
    }

    override fun showMessage(msg: String, level: Int, time: Int) {
        CTNote.getInstance(this, rootView!!).show(msg, level, time)
    }

    override fun onFragmentInteraction(uri: Uri) {

    }

    private var mNaviState = false
    private var mCONN_SERVICE_STATE = false

    override fun OnNetChanged(net: Int) {
        when (net) {
            Utils.NETWORK_NONE -> {
                showMessage(getString(R.string.tips_network_disconnected), CTNote.LEVEL_ERROR, CTNote.TIME_LONG)
            }
            Utils.NETWORK_MOBILE -> {
                if (CTConnection.getInstance(this).currentState == ConnectionState.Disconnected) {
                    CTConnection.getInstance(this).Start()
                }
            }
            Utils.NETWORK_WIFI -> {
                if (CTConnection.getInstance(this).currentState == ConnectionState.Disconnected) {
                    CTConnection.getInstance(this).Start()
                }
            }
        }
    }

    override fun OnSignalRChanged(state: Boolean) {
        if (state) {
            if (mCONN_SERVICE_STATE != state){
                showMessage(getString(R.string.tips_connected_server), CTNote.LEVEL_TIPS, CTNote.TIME_SHORT)
            }
            mMatchFrag!!.setNetworkStateIcon(state)
            mCONN_SERVICE_STATE = state
        } else {

            if (mCONN_SERVICE_STATE != state) {
                showMessage(getString(R.string.tips_reconnect_server), CTNote.LEVEL_ERROR, CTNote.TIME_SHORT)

            }
            mMatchFrag!!.setNetworkStateIcon(state)
            mHand.postDelayed({  CTConnection.getInstance(this).Start() },RECONNECT_INTERVAL)
            println("收到断开信息")
            mCONN_SERVICE_STATE = state
        }

    }

    override fun Close() {
        TalkerProgressHelper.getInstance(this).hideDialog()
        CTNote.getInstance(this, rootView!!).hide()
        this.finish()
    }

    override fun showMessage(msg: String?) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun startNewPage(target: Class<*>?) {
        startActivity(Intent(this, target), ActivityOptions.makeSceneTransitionAnimation(this).toBundle())
    }

    override fun setPresenter(presenter: MainContract.Presenter?) {

    }
    override fun showUserProfile(user: CTUser) {

        mProfileDialog!!.showUser(user)
        setBackgroundAlpha(0.5f)
    }
    private var rootView: View? = null
    private var mFollowFrag: FollowFragment? = null
    private var mMatchFrag: MatchFragment? = null
    private var mSettingFrag: SettingFragment? = null
    var viewpagerAdapter: FragAdapter? = null
    private var mProfileDialog:CTProfileCard?=null
    @SuppressLint("NewApi")
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    override fun initViews() {
        /**
         * 登录信息校验
         */
        TalkerProgressHelper.getInstance(this).show(getString(R.string.tips_userinfo_validate))
        if (GlobalVar.LOCAL_USER == null) {
            GlobalVar.LOCAL_USER = DB.getInstance(this).getLocalUser(SharedHelper.getInstance(this).getString(SharedHelper.KEY_EMAIL, ""))
        }
        mAlertDailog = AlertDialog.Builder(this).setTitle("访问受限").setCancelable(false).setMessage("您尚未进行学生认证，点击确定完善资料,取消则退出应用").setPositiveButton("确定") { p0, p1 ->
            startNewPage(ProfileActivity::class.java)
        }.setNegativeButton("取消", { p, v ->
            Close()
        }).create()
        rootView = LayoutInflater.from(this).inflate(R.layout.activity_main, null)
        if (GlobalVar.LOCAL_USER!!.State == GlobalVar.USER_STATE_WAITING) {
            AlertDialog.Builder(this).setTitle("提示").setMessage("您当前账户待核验，请您随时关注账户状态。").setPositiveButton("退出", { dialogInterface, i ->
                this.Close()
            }).setCancelable(false).show()
        }
        /**
         * 初始化fragment
         *
         */
        val fm = this.supportFragmentManager
        val mFragments = ArrayList<Fragment>()
        val find = FindFragment()
        val match = MatchFragment()
        val follow = FollowFragment()
        val settings = SettingFragment()

        find.rootview = this
        find.parentContext = this

        match.rootview = this
        match.parentContext = this

        mMatchFrag=match
        mFollowFrag = follow
        mFollowFrag!!.rootview = this
        mFollowFrag!!.parentContext = this

        mSettingFrag=settings
        mSettingFrag!!.rootview=this
        mSettingFrag!!.parentContext=this
        mFragments.add(match)
        mFragments.add(find)
        mFragments.add(follow)
        mFragments.add(settings)


        viewpagerAdapter = FragAdapter(fm, mFragments)
        viewpager.adapter = viewpagerAdapter
        /**
         * 页面滑动事件
         */
        viewpager.setOnTouchListener { view, motionEvent ->
            kotlin.run {
                if (mNaviState) {
                    btn_img_navi_switch.performClick()
                }
                false
            }
        }
        /**
         * 切换页面事件
         */
        viewpager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {

            }

            override fun onPageSelected(position: Int) {

            }
        })
        /**
         * 滑动事件
         */
        frg_navibar.setOnTouchListener(NaviTouchEvent)
        /**
         * 导航栏事件
         */
        btn_img_navi_switch.setOnClickListener {
            synchronized(this) {
                moveNaviBar(frg_navibar, mNaviState)  //滑动隐藏的导航栏
                mNaviState = !mNaviState
            }
        }
        viewpager.setPageTransformer(true, MainViewPagerTransform())
        colorAnimationView.setmViewPager(viewpager, 4, 0xffB1D3EC.toInt(), 0xff6CAAD9.toInt(), 0xff0066B2.toInt(), 0xff285577.toInt())
        radio_navi.setOnCheckedChangeListener(mRadioChanged)
        val mWidth = this.resources.getDimension(R.dimen.radio_width).toInt()
        val icon_match = this.resources.getDrawable(R.mipmap.icon_match)
        icon_match.setBounds(0, 0, mWidth, mWidth)
        val icon_find = this.resources.getDrawable(R.mipmap.icon_find)
        icon_find.setBounds(0, 0, mWidth, mWidth)
        val icon_follow = this.resources.getDrawable(R.mipmap.icon_follow)
        icon_follow.setBounds(0, 0, mWidth, mWidth)
        val icon_my = this.resources.getDrawable(R.mipmap.icon_my)
        icon_my.setBounds(0, 0, mWidth, mWidth)

        radio_match.setCompoundDrawables(null, icon_match, null, null)
        radio_find.setCompoundDrawables(null, icon_find, null, null)
        radio_follow.setCompoundDrawables(null, icon_follow, null, null)
        radio_setting.setCompoundDrawables(null, icon_my, null, null)
        if (DB.getInstance(this).getUserState(GlobalVar.LOCAL_USER!!.Uid!!) == GlobalVar.USER_STATE_UNATH) {
            //跳转到资料页面弹出资料修改
            mAlertDailog.show()
        }
        frg_navibar.visibility=if(SharedHelper.getInstance(this).getBoolean(SharedHelper.IS_SHOW_NAVI,true)) View.VISIBLE else View.INVISIBLE
        /**
         * 资料弹窗
         */
        mProfileDialog = CTProfileCard(this,rootView!!, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        mProfileDialog!!.setOnDismissListener {
            setBackgroundAlpha(1f)
        }
    }

    private val mRadioChanged = RadioGroup.OnCheckedChangeListener { p0, who ->
        run {
            when (who) {
                radio_match.id -> {
                    viewpager.setCurrentItem(0, true)
                }
                radio_find.id -> {
                    viewpager.setCurrentItem(1, true)
                }
                radio_follow.id -> {
                    viewpager.setCurrentItem(2, true)
                }
                radio_setting.id -> {
                    viewpager.setCurrentItem(3, true)
                }
            }
        }

    }
    private lateinit var mainpresenter: MainPresenter
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        setContentView(R.layout.activity_main)
        NetEventManager.getInstance().subscribe(this) //订阅网络消息
        startService(Intent(this, ConnService::class.java))
        mainpresenter = MainPresenter(this, WorkerRepository.getInstance(WorkerRemoteDataSource.getInstance()), this)
        initViews()
        gestureDetector = GestureDetector(this, listener)

        /**
         * 1.加载用户信息，学生认证校验和资料校验
         * 2.链接通讯服务器  监听网络状态 done
         * 3.加载导航 done
         * 4.子页业务逻辑
         */
    }

    /**
     * 设置添加屏幕的背景透明度
     *
     * @param bgAlpha
     *            屏幕透明度0.0-1.0 1表示完全不透明
     */
    fun setBackgroundAlpha(bgAlpha: Float) {
        val lp = (this).window.attributes;
        lp.alpha = bgAlpha
        (this).window.attributes = lp;
    }

    override fun onAttachFragment(fragment: Fragment?) {
        super.onAttachFragment(fragment)
    }


    override fun onResume() {
        if (GlobalVar.LOCAL_USER == null) {
            showMessage("登录状态异常，请您重新登录", CTNote.LEVEL_ERROR, CTNote.TIME_SHORT)
            mHand.postDelayed({
                val msg = mHand.obtainMessage()
                msg.what = 1
                mHand.sendMessage(msg)
            }, 2000)
        } else if (GlobalVar.LOCAL_USER!!.State == GlobalVar.USER_STATE_UNATH) {
            mAlertDailog.show()
        }
        TalkerProgressHelper.getInstance(this).hideDialog()
        super.onResume()
    }

    private var mHand = @SuppressLint("HandlerLeak")
    object : Handler() {
        override fun dispatchMessage(msg: Message?) {
            when (msg!!.what) {
                1 -> {
                    Close()
                }
            }
            super.dispatchMessage(msg)
        }
    }

    //{动画区
    private var gestureDetector: GestureDetector? = null
    private val listener = object : GestureDetector.OnGestureListener {
        override fun onLongPress(p0: MotionEvent?) {

        }

        override fun onShowPress(p0: MotionEvent?) {

        }

        override fun onSingleTapUp(p0: MotionEvent?): Boolean {
            return true
        }

        override fun onDown(p0: MotionEvent?): Boolean {
            return true
        }

        override fun onScroll(p0: MotionEvent?, p1: MotionEvent?, p2: Float, p3: Float): Boolean {
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
            val x = e2!!.x - e1!!.x
            val y = e2.y - e1.y

            if (x > 0 && !mNaviState) {
                btn_img_navi_switch.performClick()
            } else if (x < 0 && mNaviState) {
                btn_img_navi_switch.performClick()
            }
            return true
        }
    }
    private val NaviTouchEvent = View.OnTouchListener { view, motionEvent ->
        kotlin.run {
            gestureDetector!!.onTouchEvent(motionEvent)
            true
        }
    }

    /**
     * 旋转开关动画
     */
    private fun rotateSwitch(btn: ImageView, state: Boolean) {
        val ani: Animation = if (state) {
            AnimationUtils.loadAnimation(this, R.anim.switch_in)

        } else {
            AnimationUtils.loadAnimation(this, R.anim.switch_out)
        }
        ani.fillAfter = true
        btn.startAnimation(ani)
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun moveNaviBar(frgbar: FrameLayout, state: Boolean) {
        val ani: TranslateAnimation = if (state) {
            AnimationUtils.loadAnimation(this, R.anim.navi_out) as TranslateAnimation
        } else
            AnimationUtils.loadAnimation(this, R.anim.navi_in) as TranslateAnimation
        ani.fillAfter = true

        val parm = frgbar.layoutParams as RelativeLayout.LayoutParams
        if (state) {
            parm.marginStart = this.resources.getDimension(R.dimen.hide_navibar_width).toInt()
            radio_navi.layoutAnimation = AnimationUtils.loadLayoutAnimation(this, R.anim.navi_sub_hide)
            radio_navi.startLayoutAnimation()
        } else {
            parm.marginStart = 0
            radio_navi.layoutAnimation = AnimationUtils.loadLayoutAnimation(this, R.anim.navi_sub_show)
            radio_navi.startLayoutAnimation()
        }
        ani.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationRepeat(p0: Animation?) {
            }

            override fun onAnimationEnd(p0: Animation?) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    rotateSwitch(btn_img_navi_switch, mNaviState)
                }
            }

            override fun onAnimationStart(p0: Animation?) {
            }
        })
        frgbar.layoutParams = parm
        frgbar.startAnimation(ani)
    }
//动画区}

    private lateinit var mAlertDailog: AlertDialog

    init {

    }

    override fun onStop() {
        CTNote.getInstance(this, rootView!!).hide()
        super.onStop()
    }

    override fun onDestroy() {
        TalkerProgressHelper.hide()
        CTConnection.getInstance(this).Stop()
        stopService(Intent(this, ConnService::class.java))
        super.onDestroy()
    }


    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {

        // Used to load the 'native-lib' library on application startup.

    }
}
