package com.mrper.code23.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.boyou.autoservice.util.sysutil.ToastUtil
import com.etsy.android.grid.StaggeredGridView
import com.handmark.pulltorefresh.library.PullToRefreshBase
import com.loopj.android.http.AsyncHttpResponseHandler
import com.mrper.code23.R
import com.mrper.code23.api.HttpManager
import com.mrper.code23.data.adapter.DemoAdapter
import com.mrper.code23.ext.listener.OnSuperScrollListener
import com.mrper.code23.fewk.annotation.ContentView
import com.mrper.code23.fewk.ui.BaseActivity
import com.mrper.code23.model.DemoInfoEntry
import com.mrper.code23.model.TypeInfoEntry
import cz.msebera.android.httpclient.Header
import kotlinx.android.synthetic.main.activity_main.*
import java.util.regex.Matcher
import java.util.regex.Pattern

@ContentView(R.layout.activity_main)
class MainActivity : BaseActivity(),PullToRefreshBase.OnRefreshListener2<StaggeredGridView>,AdapterView.OnItemClickListener {

    private var demolist: MutableList<DemoInfoEntry> = mutableListOf()
    private var demoAdapter: DemoAdapter? = null
    private var currentPage: Int = 0 //当前页码
    private var typeValue: String = ""//类型名称
    private var isGetType: Boolean = false //是否已经获取过类型数据

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //设置标题部分
        toolbar.title = "23Code"
        toolbar.setTitleTextColor(Color.WHITE)
        setToolbar(toolbar)
        //设置基本控件
        lvType.onItemClickListener = this
        slideMenu.sliderFadeColor = Color.TRANSPARENT
        demoAdapter = DemoAdapter(this@MainActivity,demolist)
        lvDemo.mode = PullToRefreshBase.Mode.BOTH
        lvDemo.setOnScrollListener(OnSuperScrollListener(application))
        lvDemo.setOnRefreshListener(this)
        lvDemo.setAdapter(demoAdapter)
        getDemoList(1,true)//获取案例类型
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val item = lvType.getItemAtPosition(position) as TypeInfoEntry
        typeValue = item.typeValue
        getDemoList(1,true)
        slideMenu.closePane()
    }

    override fun onPullDownToRefresh(refreshView: PullToRefreshBase<StaggeredGridView>?) = getDemoList(1,true)//获取案例类型

    override fun onPullUpToRefresh(refreshView: PullToRefreshBase<StaggeredGridView>?) = getDemoList(currentPage + 1)

    /**
     * 获取案例列表数据
     * @param currentPage 页码
     */
    private fun getDemoList(currentPage: Int,isClearData: Boolean = false) {
        HttpManager.httpClient.get(this,HttpManager.getAbsoluteURL("$typeValue/page/$currentPage"),object: AsyncHttpResponseHandler(){
            override fun onSuccess(statusCode: Int, headers: Array<out Header>?, responseBody: ByteArray?) {
                val responseResult = String(responseBody!!,charset("utf-8"))
                if(currentPage == 1 && !isGetType) //如果是第一页，解析类型数据
                    parseDemoTypes(responseResult)//解析类型列表
                parseDemoList(responseResult,isClearData)//加载demo列表
                finishDataLoad()//完成数据加载
            }

            override fun onFailure(statusCode: Int, headers: Array<out Header>?, responseBody: ByteArray?, error: Throwable?) {
                finishDataLoad()//完成数据加载
                ToastUtil.showShortToast(context,if(statusCode == 404) "已是最后一页" else "网络错误，请检查您的网络")
            }
        })
    }

    /**
     * 解析案例类型
     * @param responseBody 结果数据
     */
    private fun parseDemoTypes(responseBody: String?){
        val htmlMatch = Pattern.compile("<li class=\"cat-item cat-item-\\d+\"><a href=\"http://www.23code.com/(.+?)/\"[^>]*>(.+?)</a>")
        val matcher = htmlMatch.matcher(responseBody)
        var typelist: MutableList<TypeInfoEntry> = mutableListOf()
        while(matcher.find()) typelist.add(TypeInfoEntry(matcher.group(2),matcher.group(1)))
        lvType.adapter = ArrayAdapter(this@MainActivity,android.R.layout.simple_list_item_1,typelist)
        isGetType = typelist.size > 0 //是否已经获取过类型数据
    }

    /**
     * 解析案例列表数据
     * @param responseBody 数据题
     * @param isClearData 是否情况数据
     */
    private fun parseDemoList(responseBody: String?,isClearData: Boolean = false){
        var matcher = regexMatcher("<article\\s+id=\"entry[-]\\d+\"[^>]+>([\\s\\S]*?)</article>",responseBody?:"")
        var demolist: MutableList<DemoInfoEntry> = mutableListOf()
        while(matcher.find()){
            val demoItem = DemoInfoEntry()
            var itemContent = matcher.group(1)
            //匹配图标
            var picMatcher = regexMatcher("<img.+?src=\"(.+?)\" class=\"attachment-thumbnail-wzh size-thumbnail-wzh wp-post-image\"[^>]+>",itemContent)
            if(picMatcher.find())
                demoItem.pic = picMatcher.group(1) //图片
            //匹配项目名称
            var nameMatcher = regexMatcher("<a href=\"(.+?)\" rel=\"bookmark\" [^>]+>([^>]+)</a>",itemContent)
            if(nameMatcher.find()) {
                demoItem.proUrl = nameMatcher.group(1)
                demoItem.proName = nameMatcher.group(2)
            }
            //匹配发布时间
            var timeMatcher = regexMatcher("<a href=\".+?\" title=\"由23Code发布\" rel=\"author\">23Code</a>([^<]+)</p>",itemContent)
            if(timeMatcher.find())
                demoItem.pubTime = timeMatcher.group(1).replace("on","")
                        .replace("年",".").replace("月",".").replace("日","")
            //匹配说明
            var desMatcher = regexMatcher("<div class=\"text\">[^<]+<p>([^<]+)</p>[^<]+</div>",itemContent)
            if(desMatcher.find())
                demoItem.desIntro = desMatcher.group(1)
            //匹配分类
            var typeMatcher = regexMatcher("<li class=\"categories\"><i[^>]+></i><a[^>]+>([^<]+)</a></li>",itemContent)
            if(typeMatcher.find())
                demoItem.typeName = typeMatcher.group(1)
            demolist.add(demoItem)
        }
        if(demolist.size > 0) {
            if (isClearData){
                this@MainActivity.currentPage = 1//设置页码为1
                this@MainActivity.demolist.clear()
            }else{
                this@MainActivity.currentPage += 1//设置页码为1
            }
            this@MainActivity.demolist.addAll(demolist)
            demoAdapter?.notifyDataSetChanged()
            if(this@MainActivity.currentPage == 1)
                lvDemo.smoothScroll2Top() //滚动大顶部去
        }else{
            ToastUtil.showShortToast(this@MainActivity,"已是最后一页")
        }
    }

    /** 正则匹配 **/
    private fun regexMatcher(pattern: String,input: String): Matcher {
        var typePattern = Pattern.compile(pattern)
        return typePattern.matcher(input)
    }

    /**  完成数据加载  **/
    private fun finishDataLoad(){
        if(lvDemo.isRefreshing) lvDemo.onRefreshComplete()
    }

    //<article\s+id="entry-\d+"[^>]+>([\s\S]*?)</article>  匹配某一列
    //<img.+?src="(.+?)" class="attachment-thumbnail-wzh size-thumbnail-wzh wp-post-image"[^>]+> 匹配ICON
    //<a href="(.+?)" rel="bookmark" [^>]+>([^>]+)</a>  匹配项目名称
    //<a href=".+?" title="由23Code发布" rel="author">23Code</a>([^<]+)</p>  匹配发布时间
    //<div class="text">[^<]+<p>([^<]+)</p>[^<]+</div>  匹配说明
    //<li class="categories"><i[^>]+></i><a[^>]+>([^<]+)</a></li> 匹配类型


}
