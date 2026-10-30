package com.craftworks.music
import org.junit.Test
import org.junit.Assert.*
import com.craftworks.music.data.model.*
class LyricRecordingMatchTest {
 private fun result(title:String="体面",artist:String="于文文",duration:Double=282.0)=LrcLibLyrics(id=1,instrumental=false,trackName=title,artistName=artist,duration=duration)
 @Test fun sameLengthDifferentSongIsRejected(){assertFalse(matchesLrcLibRecording("星月神话","金莎",282,result()))}
 @Test fun wrongSingerIsRejected(){assertFalse(matchesLrcLibRecording("体面","其他歌手",282,result()))}
 @Test fun wrongDurationIsRejected(){assertFalse(matchesLrcLibRecording("体面","于文文",270,result()))}
 @Test fun exactRecordingIsAccepted(){assertTrue(matchesLrcLibRecording("体面","于文文",282,result()))}
 @Test fun unknownArtistIsNotGuessed(){assertFalse(matchesLrcLibRecording("体面","",282,result()))}
}
