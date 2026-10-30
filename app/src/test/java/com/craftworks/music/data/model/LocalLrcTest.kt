package com.craftworks.music.data.model
import org.junit.Test
import org.junit.Assert.*
class LocalLrcTest{
 @Test fun repeatedTimestampsAndOffsets(){val rows=parseLocalLrc("[offset:-100]\n[00:01.00][00:03.200]你好");assertEquals(listOf(900,3100),rows.map{it.startMs});assertEquals("你好",rows[1].text[0])}
 @Test fun enhancedWords(){val rows=parseLocalLrc("[00:01.00]<00:01.00>星<00:01.50>月");assertEquals("星月",rows[0].text[0]);assertEquals(listOf(1000,1500),rows[0].words!!.map{it.startMs});assertEquals(1500,rows[0].words!![0].endMs)}
 @Test fun plainText(){assertEquals(-1,parseLocalLrc("没有时间戳的歌词")[0].startMs)}
 @Test fun fractionalTimestamp(){assertEquals(1234,mmssToMilliseconds("00:01.234").toInt());assertEquals(1200,mmssToMilliseconds("00:01.2").toInt())}
}
