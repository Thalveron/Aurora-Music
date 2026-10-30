package com.craftworks.music.data.repository

/** A complete, de-duplicated snapshot; a repeated backend page cannot loop forever. */
suspend fun <T> collectPages(pageSize:Int=100,key:(T)->String,fetch:suspend (Int,Int)->List<T>):List<T>{
    require(pageSize>0)
    val all=linkedMapOf<String,T>()
    var offset=0
    while(true){
        val page=fetch(pageSize,offset)
        val before=all.size
        page.forEach { all[key(it)]=it }
        if(page.size<pageSize||all.size==before)break
        offset+=page.size
    }
    return all.values.toList()
}
