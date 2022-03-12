package com.example.videotestapp.util;

public class TimeUtil {
    /**
     * 毫秒转分钟
     *
     * @param us
     * @return
     */
    public static String microsecondToClock(long us,int type) {
        return  millisecondsToClock(us/1000,type);
    }


    /**
     * 毫秒转分钟
     *
     * @param ms
     * @return
     */
    public static String millisecondsToClock(long ms,int type) {
        long seconds = 0;
        long minutes = 0;
        long hour = 0;
        long time = ms / 1000;
        seconds = time % 60;
        time /= 60;
        minutes = time % 60;
        hour = time / 60;

        StringBuilder builder = new StringBuilder();
        if(hour>10){
            builder.append(hour).append(":");
        }else if (hour>0){
            builder = builder.append("0").append(hour).append(":");
        }
        if(minutes>10){
            builder.append(minutes).append(":");
        }else{
            builder = builder.append("0").append(minutes).append(":");
        }
        if(seconds>10){
            builder.append(seconds);
        }else{
            builder = builder.append("0").append(seconds);
        }
        return builder.toString();
    }


}

