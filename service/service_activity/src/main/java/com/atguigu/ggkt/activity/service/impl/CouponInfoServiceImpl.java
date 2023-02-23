package com.atguigu.ggkt.activity.service.impl;

import cn.hutool.json.JSONUtil;
import com.alibaba.nacos.client.utils.JSONUtils;
import com.atguigu.ggkt.activity.service.CouponUseService;
import com.atguigu.ggkt.client.user.UserInfoFeignClient;
import com.atguigu.ggkt.model.activity.CouponInfo;
import com.atguigu.ggkt.activity.mapper.CouponInfoMapper;
import com.atguigu.ggkt.activity.service.CouponInfoService;
import com.atguigu.ggkt.model.activity.CouponUse;
import com.atguigu.ggkt.model.user.UserInfo;
import com.atguigu.ggkt.result.Result;
import com.atguigu.ggkt.vo.activity.CouponUseQueryVo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import springfox.documentation.spring.web.json.Json;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 优惠券信息 服务实现类
 * </p>
 *
 * @author atguigu
 * @since 2022-04-29
 */
@Service
public class CouponInfoServiceImpl extends ServiceImpl<CouponInfoMapper, CouponInfo> implements CouponInfoService {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private CouponUseService couponUseService;
    @Autowired
    private CouponInfoService couponInfoService;

    @Autowired
    private UserInfoFeignClient userInfoFeignClient;

    /**
     * 优惠券秒杀
     * @param voucherid
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherid) {
        return  null;
    }
    //获取已经使用优惠券列表（条件查询分页）
    @Override
    public IPage<CouponUse> selectCouponUsePage(Page<CouponUse> pageParam,
                                                CouponUseQueryVo couponUseQueryVo) {
        //获取条件
        Long couponId = couponUseQueryVo.getCouponId();
        String couponStatus = couponUseQueryVo.getCouponStatus();
        String getTimeBegin = couponUseQueryVo.getGetTimeBegin();
        String getTimeEnd = couponUseQueryVo.getGetTimeEnd();
        //封装条件
        QueryWrapper<CouponUse> wrapper = new QueryWrapper<>();
        if(!StringUtils.isEmpty(couponId)) {
            wrapper.eq("coupon_id",couponId);
        }
        if(!StringUtils.isEmpty(couponStatus)) {
            wrapper.eq("coupon_status",couponStatus);
        }
        if(!StringUtils.isEmpty(getTimeBegin)) {
            wrapper.ge("get_time",getTimeBegin);
        }
        if(!StringUtils.isEmpty(getTimeEnd)) {
            wrapper.le("get_time",getTimeEnd);
        }
        //调用方法条件分页查询
        IPage<CouponUse> pageModel = couponUseService.page(pageParam, wrapper);
        List<CouponUse> couponUseList = pageModel.getRecords();
        //遍历
        couponUseList.stream().forEach(item->{
            this.getUserInfoById(item);
        });
        return pageModel;
    }

    //更新优惠券
    @Override
    public void updateCouponInfoUseStatus(Long couponUseId, Long orderId) {
        CouponUse couponUse = new CouponUse();
        couponUse.setId(couponUseId);
        couponUse.setOrderId(orderId);
        couponUse.setCouponStatus("1");
        couponUse.setUsingTime(new Date());
         couponUseService.updateById(couponUse);
    }
//redis 优化获取优惠券  key="coupon:"\
    /**
     * 获取优惠券 解决超卖问题
     * @param id
     * @return
     */
    @Override
    public Result queryById(String id) {
        //缓存获取
        String key="coupon:"+id;
        String s = redisTemplate.opsForValue().get("coupon:" + id);//命中 得到信息
        if(!StringUtils.isEmpty(s))
        {
               CouponInfo cou = JSONUtil.toBean(s, CouponInfo.class);
                return Result.ok(cou);
            }
        //失败数据库获取
        CouponInfo couponInfo=getById(id);
        if(couponInfo==null) {
            //不存在 返回错误信息
            return Result.fail("无优惠券");
        }
       redisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(couponInfo),30L, TimeUnit.MINUTES);
        System.out.println(1);
        return null;

    }

    @Override
    @Transactional
    public Result update(CouponInfo couponInfo) {
        Long id=couponInfo.getId();
        if(id==null)
            return Result.fail("优惠券不存在");
          couponInfoService.updateById(couponInfo);
          redisTemplate.delete("coupon:"+id);
          return Result.ok(couponInfo);
    }



    //根据用户id，通过远程调用得到用户信息
    private CouponUse getUserInfoById(CouponUse couponUse) {
        //获取用户id
        Long userId = couponUse.getUserId();
        if(!StringUtils.isEmpty(userId)) {
            //远程调用
            UserInfo userInfo = userInfoFeignClient.getById(userId);
            if(userInfo != null) {
                couponUse.getParam().put("nickName",userInfo.getNickName());
                couponUse.getParam().put("phone",userInfo.getPhone());
            }
        }
        return couponUse;
    }
}
