package com.kirox.app.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class KiroXIcon {
    Contacts, Chat, Settings, Profile, Search, ReadAll, Compose, Add, Call, Chevron,
    Palette, Folder, Bell, Shield, Storage, Devices, Star, Sliders, Refresh, Info,
    Edit, More, Stories, Media, Bookmark, Check, Pin, CallIncoming, CallOutgoing, CallMissed,
    Camera, At, Phone, Cake, File, Link, Music, Voice, Verified, Photo,
    Back, Video, Attach, Send, Emoji, Copy, Forward, Delete, Location, Reply,
    Chats, Tune, ProfileCircle, Qr, Speaker, CallEnd, FlipCamera, Menu, Lock, Eye, Backspace
}

@Composable
fun KiroXIcon(
    icon: KiroXIcon,
    description: String?,
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier.semantics { if (description != null) contentDescription = description }) {
        val sx = size.width / 24f
        val sy = size.height / 24f
        val stroke = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * sx, y * sy)
        fun line(a: Offset, b: Offset) = drawLine(color, a, b, stroke.width, StrokeCap.Round)
        fun circle(cx: Float, cy: Float, r: Float) = drawCircle(color, r * sx, p(cx, cy), style = stroke)
        fun rect(l: Float, t: Float, r: Float, b: Float, radius: Float = 2f) = drawRoundRect(color, p(l,t), androidx.compose.ui.geometry.Size((r-l)*sx,(b-t)*sy), androidx.compose.ui.geometry.CornerRadius(radius*sx), style = stroke)
        fun path(points: List<Offset>, close: Boolean = false) { val q = Path(); points.forEachIndexed { index, point -> if (index == 0) q.moveTo(point.x,point.y) else q.lineTo(point.x,point.y) }; if(close) q.close(); drawPath(q,color,style=stroke) }
        fun path(a: Offset, b: Offset, c: Offset? = null, d: Offset? = null, e: Offset? = null, f: Offset? = null, g: Offset? = null, h: Offset? = null, i: Offset? = null, j: Offset? = null, k: Offset? = null, close: Boolean = false) = path(listOfNotNull(a,b,c,d,e,f,g,h,i,j,k), close)
        when (icon) {
            KiroXIcon.Search -> { circle(10.5f,10.5f,6f); line(p(15f,15f),p(20f,20f)) }
            KiroXIcon.Chat -> { drawOval(color, topLeft = p(3f,4f), size = Size(18f*sx,14f*sy), style=stroke); path(p(7f,18f),p(5f,21f),p(11f,18f)) }
            KiroXIcon.Contacts -> { circle(9f,8f,3f); circle(17f,9f,2.5f); path(p(3f,20f),p(3.5f,17f),p(5.5f,14.5f),p(9f,14f),p(12.5f,14.5f),p(14f,17f),p(14f,20f)); path(p(15f,15f),p(18f,14.5f),p(21f,16f),p(21f,20f)) }
            KiroXIcon.Profile -> { circle(12f,7.5f,3.5f); path(p(5f,21f),p(5.5f,17f),p(8f,14f),p(12f,13f),p(16f,14f),p(18.5f,17f),p(19f,21f)) }
            KiroXIcon.Settings -> { circle(12f,12f,3f); circle(12f,12f,8f); line(p(12f,2f),p(12f,4f)); line(p(12f,20f),p(12f,22f)); line(p(2f,12f),p(4f,12f)); line(p(20f,12f),p(22f,12f)) }
            KiroXIcon.ReadAll -> { path(p(2f,12f),p(6f,16f),p(13f,8f)); path(p(9f,13f),p(12f,16f),p(21f,6f)) }
            KiroXIcon.Compose, KiroXIcon.Edit -> { rect(4f,5f,18f,20f); path(p(9f,15f),p(10f,11f),p(18f,3f),p(21f,6f),p(13f,14f),p(9f,15f)) }
            KiroXIcon.Add -> { line(p(12f,5f),p(12f,19f)); line(p(5f,12f),p(19f,12f)) }
            KiroXIcon.Call -> { path(listOf(p(6f,3f),p(9f,4f),p(10f,8f),p(8f,10f),p(10f,14f),p(14f,16f),p(16f,14f),p(20f,15f),p(21f,18f),p(18f,21f),p(14f,20f),p(8f,17f),p(4f,12f),p(3f,6f),p(6f,3f))) }
            KiroXIcon.Chevron -> path(p(9f,5f),p(16f,12f),p(9f,19f))
            KiroXIcon.Palette -> { circle(12f,12f,8f); circle(9f,8f,0.8f); circle(14f,7f,0.8f); circle(17f,11f,0.8f); path(p(12f,20f),p(10f,17f),p(12f,14f),p(16f,15f)) }
            KiroXIcon.Folder -> path(p(3f,7f),p(9f,7f),p(11f,9f),p(21f,9f),p(20f,19f),p(4f,19f),p(3f,7f),close=true)
            KiroXIcon.Bell -> { path(p(5f,17f),p(7f,15f),p(7f,10f),p(9f,6f),p(12f,5f),p(15f,6f),p(17f,10f),p(17f,15f),p(19f,17f),p(5f,17f)); path(p(10f,20f),p(12f,21f),p(14f,20f)) }
            KiroXIcon.Shield -> path(p(12f,3f),p(20f,6f),p(19f,14f),p(16f,19f),p(12f,21f),p(8f,19f),p(5f,14f),p(4f,6f),p(12f,3f),close=true)
            KiroXIcon.Storage -> { rect(4f,3f,20f,21f); line(p(8f,17f),p(16f,17f)) }
            KiroXIcon.Devices -> { rect(3f,4f,16f,15f); rect(15f,9f,21f,20f); line(p(8f,19f),p(13f,19f)) }
            KiroXIcon.Star -> path(p(12f,3f),p(15f,9f),p(21f,10f),p(16.5f,15f),p(18f,21f),p(12f,18f),p(6f,21f),p(7.5f,15f),p(3f,10f),p(9f,9f),p(12f,3f),close=true)
            KiroXIcon.Sliders -> { line(p(4f,7f),p(20f,7f)); line(p(4f,17f),p(20f,17f)); circle(9f,7f,1.8f); circle(15f,17f,1.8f) }
            KiroXIcon.Refresh -> { path(p(19f,8f),p(19f,4f),p(15f,4f)); path(p(19f,5f),p(16f,3.5f),p(12f,3f),p(7f,5f),p(4f,9f)); path(p(5f,16f),p(5f,20f),p(9f,20f)); path(p(5f,19f),p(8f,21f),p(13f,21f),p(18f,18f),p(20f,14f)) }
            KiroXIcon.Info -> { circle(12f,12f,9f); line(p(12f,11f),p(12f,17f)); circle(12f,7.5f,.5f) }
            KiroXIcon.Lock -> { rect(5f,10.5f,19f,21f,2.5f); path(p(8f,10.5f),p(8f,7.5f),p(9.2f,4.6f),p(12f,3.5f),p(14.8f,4.6f),p(16f,7.5f),p(16f,10.5f)); line(p(12f,14.5f),p(12f,17f)) }
            KiroXIcon.Eye -> { path(p(2.5f,12f),p(5.5f,8f),p(9f,6f),p(12f,5.5f),p(15f,6f),p(18.5f,8f),p(21.5f,12f),p(18.5f,16f),p(15f,18f),p(12f,18.5f),p(9f,18f),close=false); path(p(9f,18f),p(5.5f,16f),p(2.5f,12f)); circle(12f,12f,3f) }
            KiroXIcon.Backspace -> { path(p(8f,5f),p(21f,5f),p(21f,19f),p(8f,19f),p(2.5f,12f),close=true); line(p(11.5f,9f),p(17.5f,15f)); line(p(17.5f,9f),p(11.5f,15f)) }
            KiroXIcon.Menu -> { line(p(4f,7f),p(20f,7f)); line(p(4f,12f),p(20f,12f)); line(p(4f,17f),p(20f,17f)) }
            KiroXIcon.More -> { circle(12f,5f,.8f); circle(12f,12f,.8f); circle(12f,19f,.8f) }
            KiroXIcon.Stories -> { circle(12f,12f,8f); circle(12f,12f,3f) }
            KiroXIcon.Media -> { rect(3f,4f,21f,20f); circle(8f,9f,1.5f); path(p(4f,18f),p(9f,13f),p(13f,16f),p(16f,12f),p(21f,17f)) }
            KiroXIcon.Bookmark -> path(p(6f,3f),p(18f,3f),p(18f,21f),p(12f,17f),p(6f,21f),p(6f,3f),close=true)
            KiroXIcon.Check -> path(p(5f,12f),p(10f,17f),p(19f,7f))
            KiroXIcon.Pin -> { path(p(8f,4f),p(16f,4f),p(15f,10f),p(19f,14f),p(5f,14f),p(9f,10f),p(8f,4f)); line(p(12f,14f),p(12f,21f)) }
            KiroXIcon.CallIncoming -> { line(p(18f,6f),p(8f,16f)); path(p(8f,10f),p(8f,16f),p(14f,16f)) }
            KiroXIcon.CallOutgoing -> { line(p(7f,17f),p(17f,7f)); path(p(11f,7f),p(17f,7f),p(17f,13f)) }
            KiroXIcon.CallMissed -> { line(p(7f,17f),p(17f,7f)); path(p(11f,7f),p(17f,7f),p(17f,13f)); line(p(6f,6f),p(10f,10f)); line(p(10f,6f),p(6f,10f)) }
            KiroXIcon.Camera -> { rect(3f,7f,21f,19f,3f); path(p(8f,7f),p(10f,4f),p(14f,4f),p(16f,7f)); circle(12f,13f,3f) }
            KiroXIcon.At -> { circle(12f,12f,4f); path(p(16f,12f),p(16f,16f),p(18f,17f),p(20f,15f),p(20f,11f),p(18f,7f),p(14f,4f),p(9f,4f),p(5f,7f),p(3f,12f),p(5f,17f)); path(p(5f,17f),p(9f,20f),p(14f,20f)) }
            KiroXIcon.Phone -> { rect(7f,2f,17f,22f,2f); line(p(10f,18f),p(14f,18f)) }
            KiroXIcon.Cake -> { rect(4f,11f,20f,20f,2f); line(p(4f,15f),p(20f,15f)); line(p(12f,6f),p(12f,11f)); path(p(10f,5f),p(12f,2f),p(14f,5f)) }
            KiroXIcon.File -> { path(p(6f,2f),p(14f,2f),p(19f,7f),p(19f,22f),p(6f,22f),p(6f,2f),close=true); path(p(14f,2f),p(14f,7f),p(19f,7f)) }
            KiroXIcon.Link -> { path(p(10f,14f),p(8f,16f),p(5f,16f),p(3f,14f),p(3f,11f),p(7f,7f),p(10f,7f)); path(p(14f,10f),p(16f,8f),p(19f,8f),p(21f,10f),p(21f,13f),p(17f,17f),p(14f,17f)); line(p(8f,12f),p(16f,12f)) }
            KiroXIcon.Music -> { line(p(9f,5f),p(9f,17f)); line(p(9f,5f),p(19f,3f)); line(p(19f,3f),p(19f,15f)); circle(6f,18f,3f); circle(16f,16f,3f) }
            KiroXIcon.Voice -> { rect(9f,3f,15f,15f,3f); path(p(5f,11f),p(6f,15f),p(9f,18f),p(12f,19f),p(15f,18f),p(18f,15f),p(19f,11f)); line(p(12f,19f),p(12f,22f)); line(p(8f,22f),p(16f,22f)) }
            KiroXIcon.Verified -> { circle(12f,12f,9f); path(p(7f,12f),p(10.5f,15.5f),p(17f,8.5f)) }
            KiroXIcon.Photo -> { rect(3f,4f,21f,20f); circle(9f,9f,1.5f); path(p(4f,18f),p(9f,13f),p(13f,16f),p(17f,11f),p(21f,16f)) }
            KiroXIcon.Back -> { line(p(20f,12f),p(5f,12f)); path(p(11f,5f),p(4f,12f),p(11f,19f)) }
            KiroXIcon.Video -> { rect(3f,6f,16f,18f,3f); path(p(16f,10f),p(21f,7f),p(21f,17f),p(16f,14f)) }
            KiroXIcon.Attach -> { path(p(8f,12f),p(14f,6f),p(18f,7f),p(19f,11f),p(10f,20f),p(5f,19f),p(4f,14f),p(14f,4f),p(19f,5f)) }
            KiroXIcon.Send -> { path(p(3f,4f),p(22f,12f),p(3f,20f),p(7f,12f),p(3f,4f),close=true); line(p(7f,12f),p(15f,12f)) }
            KiroXIcon.Emoji -> { circle(12f,12f,9f); circle(9f,10f,.7f); circle(15f,10f,.7f); path(p(8f,15f),p(10f,17f),p(14f,17f),p(16f,15f)) }
            KiroXIcon.Copy -> { rect(7f,7f,20f,21f); rect(4f,3f,17f,17f) }
            KiroXIcon.Forward -> { path(p(4f,18f),p(8f,11f),p(16f,9f)); path(p(13f,5f),p(20f,9f),p(13f,14f)) }
            KiroXIcon.Delete -> { rect(6f,7f,18f,21f,1f); line(p(4f,7f),p(20f,7f)); line(p(9f,4f),p(15f,4f)); line(p(10f,11f),p(10f,17f)); line(p(14f,11f),p(14f,17f)) }
            KiroXIcon.Location -> { path(p(12f,22f),p(6f,14f),p(5f,10f),p(7f,5f),p(12f,2f),p(17f,5f),p(19f,10f),p(18f,14f),p(12f,22f)); circle(12f,10f,2.5f) }
            KiroXIcon.Chats -> {
                // Two overlapping speech bubbles: back one top-right, front one bottom-left with a tail.
                path(p(8.5f,7.5f),p(8.5f,5.5f),p(10.5f,3.5f),p(18.5f,3.5f),p(20.5f,5.5f),p(20.5f,11f),p(18.5f,13f),p(17.5f,13f),p(17.5f,15.5f),p(15.5f,13.8f))
                path(p(5.5f,8f),p(13.5f,8f),p(15.5f,10f),p(15.5f,16f),p(13.5f,18f),p(9.5f,18f),p(5.5f,21f),p(5.5f,18f),p(3.5f,16f),p(3.5f,10f),p(5.5f,8f),close=true)
            }
            KiroXIcon.Tune -> {
                // Three sliders; lines stop at each knob so the glyph works on any background.
                line(p(4f,6f),p(12.8f,6f)); line(p(17.2f,6f),p(20f,6f)); circle(15f,6f,2.2f)
                line(p(4f,12f),p(6.8f,12f)); line(p(11.2f,12f),p(20f,12f)); circle(9f,12f,2.2f)
                line(p(4f,18f),p(13.8f,18f)); line(p(18.2f,18f),p(20f,18f)); circle(16f,18f,2.2f)
            }
            KiroXIcon.ProfileCircle -> { circle(12f,12f,9f); circle(12f,9.5f,3.2f); path(p(6.3f,18.6f),p(8f,16f),p(12f,15f),p(16f,16f),p(17.7f,18.6f)) }
            KiroXIcon.Qr -> { rect(3.5f,3.5f,10f,10f,1.5f); rect(14f,3.5f,20.5f,10f,1.5f); rect(3.5f,14f,10f,20.5f,1.5f); line(p(14f,14f),p(14f,15f)); line(p(17.5f,14f),p(20.5f,14f)); line(p(14f,18f),p(17f,18f)); line(p(20.5f,17.5f),p(20.5f,20.5f)); line(p(17f,20.5f),p(17.5f,20.5f)) }
            KiroXIcon.Speaker -> { path(p(4f,9f),p(8f,9f),p(13f,5f),p(13f,19f),p(8f,15f),p(4f,15f),close=true); path(p(16f,9f),p(17.2f,12f),p(16f,15f)); path(p(18.5f,6.5f),p(20.5f,12f),p(18.5f,17.5f)) }
            KiroXIcon.CallEnd -> { path(listOf(p(2.5f,14f),p(3f,11f),p(7f,9f),p(12f,8.5f),p(17f,9f),p(21f,11f),p(21.5f,14f),p(18.5f,15.5f),p(16f,13f),p(16f,11.5f),p(12f,11f),p(8f,11.5f),p(8f,13f),p(5.5f,15.5f),p(2.5f,14f))) }
            KiroXIcon.FlipCamera -> { rect(3f,7f,21f,19f,3f); path(p(8f,7f),p(10f,4f),p(14f,4f),p(16f,7f)); path(p(9f,12f),p(10.5f,10.5f),p(13.5f,10.5f),p(15f,12.5f)); path(p(15f,14f),p(13.5f,15.5f),p(10.5f,15.5f),p(9f,13.5f)); path(p(15f,10.5f),p(15f,12.5f),p(13f,12.5f)); path(p(9f,15.5f),p(9f,13.5f),p(11f,13.5f)) }
            KiroXIcon.Reply -> { path(p(10f,6f),p(4f,12f),p(10f,18f)); path(p(5f,12f),p(14f,12f),p(20f,17f)) }
        }
    }
}
