package com.example.engwolf // <-- ЗАМЕНИ ЭТО на свою первую строчку из файла!

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
// Вот эти три строки должны перестать быть красными после Invalidate Caches
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { // <--- Начало
            MaterialTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "first") {
                    composable("first") {
                        // Твой экран с кнопкой
                        FirstScreen(navController)
                    }
                    composable("second") {
                        // Твой пустой экран
                        Surface(modifier = Modifier.fillMaxSize()) {}
                    }
                }
            }
        } // <--- Конец
    }
}

@androidx.compose.runtime.Composable
fun FirstScreen(navController: androidx.navigation.NavHostController) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = { navController.navigate("second") }) {
            Text("Жми")
        }
    }
}
