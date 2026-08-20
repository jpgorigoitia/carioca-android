package com.carioca.game

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carioca.game.domain.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { CariocaApp() } }
}

private val Navy=Color(0xFF102A43); private val Teal=Color(0xFF1E7A73); private val Gold=Color(0xFFFFC857)

@Composable fun CariocaApp() {
    var mode by remember { mutableStateOf(GameMode.REGULAR) }
    var screen by remember { mutableStateOf("menu") }
    var players by remember { mutableIntStateOf(4) }
    var difficulty by remember { mutableStateOf(Difficulty.MEDIUM) }
    MaterialTheme(colorScheme=lightColorScheme(primary=Teal,secondary=Gold)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Navy,Color(0xFF214E67)))).padding(20.dp)) {
            when(screen) {
                "menu" -> Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Spacer(Modifier.height(34.dp)); Text("CARIOCA",fontSize=42.sp,fontWeight=FontWeight.Black,color=Color.White)
                    Text("Real cards. Clear rules. Your table.",color=Color(0xFFD9EAF2))
                    SegmentedButtonRow { GameMode.entries.forEachIndexed { i,m -> SegmentedButton(selected=mode==m,onClick={mode=m},shape=SegmentedButtonDefaults.itemShape(i,2)){Text("${m.name.lowercase().replaceFirstChar{it.uppercase()}} · ${m.rounds} rounds")} } }
                    MenuButton("Play Against AI") { screen="practice" }; MenuButton("Private Game · Coming soon"){}; MenuButton("Rules") { screen="rules" }
                    Spacer(Modifier.weight(1f)); Text("Ad-free · No wagering",color=Color(0xFFA9C7D4))
                }
                "practice" -> Practice(mode,players,difficulty,{players=it},{difficulty=it},{screen="table"},{screen="menu"})
                "rules" -> Rules { screen="menu" }
                else -> Table(mode,players,difficulty){screen="menu"}
            }
        }
    }
}

@Composable private fun MenuButton(text:String,onClick:()->Unit)=Button(onClick=onClick,modifier=Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(16.dp)){Text(text,fontSize=17.sp)}

@Composable private fun Practice(mode:GameMode,players:Int,difficulty:Difficulty,setPlayers:(Int)->Unit,setDifficulty:(Difficulty)->Unit,start:()->Unit,back:()->Unit){
    Column(verticalArrangement=Arrangement.spacedBy(18.dp)) { TextButton(onClick=back){Text("← Back",color=Color.White)}; Text("Practice Setup",fontSize=30.sp,fontWeight=FontWeight.Bold,color=Color.White); Text("${mode.name.lowercase().replaceFirstChar{it.uppercase()}} game · ${mode.rounds} rounds",color=Gold)
        Text("Total players",color=Color.White); Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){(2..4).forEach{FilterChip(selected=players==it,onClick={setPlayers(it)},label={Text("$it")})}}
        Text("AI difficulty",color=Color.White); Difficulty.entries.forEach{FilterChip(selected=difficulty==it,onClick={setDifficulty(it)},label={Text(it.name.lowercase().replaceFirstChar{x->x.uppercase()})})}; Spacer(Modifier.weight(1f)); MenuButton("Start Practice",start)
    }
}

@Composable private fun Table(mode:GameMode,players:Int,difficulty:Difficulty,back:()->Unit){
    var steal by remember { mutableStateOf(true) }; var round by remember { mutableIntStateOf(1) }
    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton(onClick=back){Text("Exit",color=Color.White)};Text("Round $round/${mode.rounds}",color=Color.White,fontWeight=FontWeight.Bold)}; Text(GameRules.rounds[round-1].let{ if(it.special!=null) it.special.name.replace('_',' ') else "${it.legs} legs · ${it.straights} straights"},color=Gold)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceAround){repeat(players-1){Text("AI ${it+1}\n7 cards",color=Color.White)}}; Spacer(Modifier.height(28.dp)); Row(horizontalArrangement=Arrangement.spacedBy(24.dp)){CardPile("DRAW\n54"); ShiningDiscard(steal){ToneGenerator(AudioManager.STREAM_MUSIC,70).startTone(ToneGenerator.TONE_PROP_ACK,130)}}
        Text(if(steal)"Available to steal · +2 points" else "Discard pile",color=if(steal)Gold else Color.White); Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={steal=false}){Text("Draw")}; Button(onClick={steal=false},enabled=steal){Text("Steal")}}
        Spacer(Modifier.weight(1f)); Text("Your hand · ${difficulty.name.lowercase()} AI",color=Color.White); Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("A♥","4♣","5♣","6♣","7♣","JOKER").forEach{MiniCard(it)}}; Button(onClick={round=if(round==mode.rounds) 1 else round+1;steal=true}){Text("Demo next round")}
    }
}

@Composable private fun CardPile(label:String)=Box(Modifier.size(88.dp,122.dp).background(Color(0xFF3273A8),RoundedCornerShape(10.dp)).border(2.dp,Color.White,RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){Text(label,color=Color.White,fontWeight=FontWeight.Bold)}
@Composable private fun ShiningDiscard(active:Boolean,onActive:()->Unit){LaunchedEffect(active){if(active)onActive()};val t=rememberInfiniteTransition(label="shine");val a by t.animateFloat(.35f,1f,infiniteRepeatable(tween(750),RepeatMode.Reverse),label="a");Box(Modifier.size(88.dp,122.dp).background(Color.White,RoundedCornerShape(10.dp)).border(if(active)6.dp else 2.dp,if(active)Gold.copy(alpha=a) else Color.Gray,RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){Text("9 ♦",color=Color.Red,fontSize=24.sp,fontWeight=FontWeight.Bold)}}
@Composable private fun MiniCard(label:String)=Box(Modifier.width(49.dp).height(78.dp).background(Color.White,RoundedCornerShape(7.dp)).clickable{}.padding(5.dp)){Text(label,color=if(label.contains('♥')||label.contains('♦'))Color.Red else Navy,fontWeight=FontWeight.Bold,fontSize=12.sp)}
@Composable private fun Rules(back:()->Unit)=Column(verticalArrangement=Arrangement.spacedBy(12.dp)){TextButton(onClick=back){Text("← Back",color=Color.White)};Text("Rules",fontSize=30.sp,fontWeight=FontWeight.Bold,color=Color.White);listOf("Jokers are the only wildcards.","Only one Joker per meld; it cannot be replaced.","Regular has 8 rounds; Special has 11.","A steal adds 2 points.","Going out has no −30 bonus.","Lowest cumulative score wins.").forEach{Text("• $it",color=Color.White,fontSize=17.sp)}}
