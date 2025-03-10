
package com.controller;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.text.SimpleDateFormat;
import com.alibaba.fastjson.JSONObject;
import java.util.*;
import org.springframework.beans.BeanUtils;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.context.ContextLoader;
import javax.servlet.ServletContext;
import com.service.TokenService;
import com.utils.*;
import java.lang.reflect.InvocationTargetException;

import com.service.DictionaryService;
import org.apache.commons.lang3.StringUtils;
import com.annotation.IgnoreAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.mapper.Wrapper;
import com.entity.*;
import com.entity.view.*;
import com.service.*;
import com.utils.PageUtils;
import com.utils.R;
import com.alibaba.fastjson.*;

/**
 * 建议
 * 后端接口
 * @author
 * @email
*/
@RestController
@Controller
@RequestMapping("/jianyi")
public class JianyiController {
    private static final Logger logger = LoggerFactory.getLogger(JianyiController.class);

    @Autowired
    private JianyiService jianyiService;


    @Autowired
    private TokenService tokenService;
    @Autowired
    private DictionaryService dictionaryService;

    //级联表service
    @Autowired
    private YonghuService yonghuService;

    @Autowired
    private GongzuorenyuanService gongzuorenyuanService;


    /**
    * 后端列表
    */
    @RequestMapping("/page")
    public R page(@RequestParam Map<String, Object> params, HttpServletRequest request){
        logger.debug("page方法:,,Controller:{},,params:{}",this.getClass().getName(),JSONObject.toJSONString(params));
        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(false)
            return R.error(511,"永不会进入");
        else if("用户".equals(role))
            params.put("yonghuId",request.getSession().getAttribute("userId"));
        else if("工作人员".equals(role))
            params.put("gongzuorenyuanId",request.getSession().getAttribute("userId"));
        if(params.get("orderBy")==null || params.get("orderBy")==""){
            params.put("orderBy","id");
        }
        PageUtils page = jianyiService.queryPage(params);

        //字典表数据转换
        List<JianyiView> list =(List<JianyiView>)page.getList();
        for(JianyiView c:list){
            //修改对应字典表字段
            dictionaryService.dictionaryConvert(c, request);
        }
        return R.ok().put("data", page);
    }

    /**
    * 后端详情
    */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id, HttpServletRequest request){
        logger.debug("info方法:,,Controller:{},,id:{}",this.getClass().getName(),id);
        JianyiEntity jianyi = jianyiService.selectById(id);
        if(jianyi !=null){
            //entity转view
            JianyiView view = new JianyiView();
            BeanUtils.copyProperties( jianyi , view );//把实体数据重构到view中

                //级联表
                YonghuEntity yonghu = yonghuService.selectById(jianyi.getYonghuId());
                if(yonghu != null){
                    BeanUtils.copyProperties( yonghu , view ,new String[]{ "id", "createTime", "insertTime", "updateTime"});//把级联的数据添加到view中,并排除id和创建时间字段
                    view.setYonghuId(yonghu.getId());
                }
            //修改对应字典表字段
            dictionaryService.dictionaryConvert(view, request);
            return R.ok().put("data", view);
        }else {
            return R.error(511,"查不到数据");
        }

    }

    /**
    * 后端保存
    */
    @RequestMapping("/save")
    public R save(@RequestBody JianyiEntity jianyi, HttpServletRequest request){
        logger.debug("save方法:,,Controller:{},,jianyi:{}",this.getClass().getName(),jianyi.toString());

        String role = String.valueOf(request.getSession().getAttribute("role"));
        if(false)
            return R.error(511,"永远不会进入");
        else if("用户".equals(role))
            jianyi.setYonghuId(Integer.valueOf(String.valueOf(request.getSession().getAttribute("userId"))));

        Wrapper<JianyiEntity> queryWrapper = new EntityWrapper<JianyiEntity>()
            .eq("yonghu_id", jianyi.getYonghuId())
            .eq("jianyi_uuid_number", jianyi.getJianyiUuidNumber())
            .eq("jianyi_name", jianyi.getJianyiName())
            .eq("jianyi_types", jianyi.getJianyiTypes())
            .eq("huifu_text", jianyi.getHuifuText())
            ;

        logger.info("sql语句:"+queryWrapper.getSqlSegment());
        JianyiEntity jianyiEntity = jianyiService.selectOne(queryWrapper);
        if(jianyiEntity==null){
            jianyi.setInsertTime(new Date());
            jianyi.setCreateTime(new Date());
            jianyiService.insert(jianyi);
            return R.ok();
        }else {
            return R.error(511,"表中有相同数据");
        }
    }

    /**
    * 后端修改
    */
    @RequestMapping("/update")
    public R update(@RequestBody JianyiEntity jianyi, HttpServletRequest request){
        logger.debug("update方法:,,Controller:{},,jianyi:{}",this.getClass().getName(),jianyi.toString());

        String role = String.valueOf(request.getSession().getAttribute("role"));

            jianyi.setUpdateTime(new Date());
            jianyiService.updateById(jianyi);//根据id更新
            return R.ok();

    }



    /**
    * 删除
    */
    @RequestMapping("/delete")
    public R delete(@RequestBody Integer[] ids){
        logger.debug("delete:,,Controller:{},,ids:{}",this.getClass().getName(),ids.toString());
        jianyiService.deleteBatchIds(Arrays.asList(ids));
        return R.ok();
    }


    /**
     * 批量上传
     */
    @RequestMapping("/batchInsert")
    public R save( String fileName, HttpServletRequest request){
        logger.debug("batchInsert方法:,,Controller:{},,fileName:{}",this.getClass().getName(),fileName);
        Integer yonghuId = Integer.valueOf(String.valueOf(request.getSession().getAttribute("userId")));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            List<JianyiEntity> jianyiList = new ArrayList<>();//上传的东西
            Map<String, List<String>> seachFields= new HashMap<>();//要查询的字段
            Date date = new Date();
            int lastIndexOf = fileName.lastIndexOf(".");
            if(lastIndexOf == -1){
                return R.error(511,"该文件没有后缀");
            }else{
                String suffix = fileName.substring(lastIndexOf);
                if(!".xls".equals(suffix)){
                    return R.error(511,"只支持后缀为xls的excel文件");
                }else{
                    URL resource = this.getClass().getClassLoader().getResource("../../upload/" + fileName);//获取文件路径
                    File file = new File(resource.getFile());
                    if(!file.exists()){
                        return R.error(511,"找不到上传文件，请联系管理员");
                    }else{
                        List<List<String>> dataList = PoiUtil.poiImport(file.getPath());//读取xls文件
                        dataList.remove(0);//删除第一行，因为第一行是提示
                        for(List<String> data:dataList){
                            //循环
                            JianyiEntity jianyiEntity = new JianyiEntity();
//                            jianyiEntity.setYonghuId(Integer.valueOf(data.get(0)));   //用户 要改的
//                            jianyiEntity.setJianyiUuidNumber(data.get(0));                    //建议编号 要改的
//                            jianyiEntity.setJianyiName(data.get(0));                    //建议标题 要改的
//                            jianyiEntity.setJianyiTypes(Integer.valueOf(data.get(0)));   //建议类型 要改的
//                            jianyiEntity.setJianyiContent("");//详情和图片
//                            jianyiEntity.setInsertTime(date);//时间
//                            jianyiEntity.setHuifuText(data.get(0));                    //回复内容 要改的
//                            jianyiEntity.setUpdateTime(sdf.parse(data.get(0)));          //回复时间 要改的
//                            jianyiEntity.setCreateTime(date);//时间
                            jianyiList.add(jianyiEntity);


                            //把要查询是否重复的字段放入map中
                                //建议编号
                                if(seachFields.containsKey("jianyiUuidNumber")){
                                    List<String> jianyiUuidNumber = seachFields.get("jianyiUuidNumber");
                                    jianyiUuidNumber.add(data.get(0));//要改的
                                }else{
                                    List<String> jianyiUuidNumber = new ArrayList<>();
                                    jianyiUuidNumber.add(data.get(0));//要改的
                                    seachFields.put("jianyiUuidNumber",jianyiUuidNumber);
                                }
                        }

                        //查询是否重复
                         //建议编号
                        List<JianyiEntity> jianyiEntities_jianyiUuidNumber = jianyiService.selectList(new EntityWrapper<JianyiEntity>().in("jianyi_uuid_number", seachFields.get("jianyiUuidNumber")));
                        if(jianyiEntities_jianyiUuidNumber.size() >0 ){
                            ArrayList<String> repeatFields = new ArrayList<>();
                            for(JianyiEntity s:jianyiEntities_jianyiUuidNumber){
                                repeatFields.add(s.getJianyiUuidNumber());
                            }
                            return R.error(511,"数据库的该表中的 [建议编号] 字段已经存在 存在数据为:"+repeatFields.toString());
                        }
                        jianyiService.insertBatch(jianyiList);
                        return R.ok();
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
            return R.error(511,"批量插入数据异常，请联系管理员");
        }
    }






}
