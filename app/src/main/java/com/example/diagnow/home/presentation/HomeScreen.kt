package com.example.diagnow.home.presentation

import android.app.Application // Importar Application
import android.util.Log // Importar Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration // Importar SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope // Importar CoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider // Importar Factory
import androidx.lifecycle.viewmodel.compose.viewModel // Importar viewModel composable
import com.example.diagnow.DiagNowApplication
import com.example.diagnow.core.database.repository.LocalDataRepository
import com.example.diagnow.core.network.RetrofitHelper
import com.example.diagnow.core.session.SessionManager
import com.example.diagnow.home.data.repository.PrescriptionRepository
import com.example.diagnow.home.domain.GetPrescriptionsUseCase
import com.example.diagnow.home.presentation.components.PrescriptionCard
import kotlinx.coroutines.launch // Importar launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPrescriptionClick: (String, String) -> Unit,
    onLogout: () -> Unit // Lambda para navegar fuera
) {
    // --- Dependencias ---
    val context = LocalContext.current
    val application = context.applicationContext as Application // Obtener Application
    val sessionManager = remember { SessionManager(application) } // Pasar application
    val retrofitHelper = remember { RetrofitHelper(sessionManager) }
    val database = remember { (application as DiagNowApplication).database }
    val prescriptionDao = remember { database.prescriptionDao() }
    val medicationDao = remember { database.medicationDao() }
    val localRepository = remember { LocalDataRepository(database, prescriptionDao, medicationDao) }
    val getPrescriptionsUseCase = remember {
        GetPrescriptionsUseCase(
            remoteRepository = PrescriptionRepository(retrofitHelper, sessionManager),
            localRepository = localRepository
        )
    }

    // --- Instanciar ViewModel con Factory ---
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(
            application = application,
            getPrescriptionsUseCase = getPrescriptionsUseCase,
            localRepository = localRepository,
            sessionManager = sessionManager
        )
    )

    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope() // Scope para lanzar corutinas desde la UI

    // --- Manejar errores con Snackbar ---
    LaunchedEffect(uiState.error) {
        uiState.error?.let { errorMsg ->
            scope.launch { // Lanzar en scope
                snackbarHostState.showSnackbar(
                    message = errorMsg,
                    duration = SnackbarDuration.Short // Duración corta para errores
                )
                // Considerar NO limpiar el error aquí para que persista si es necesario
                // viewModel.clearError()
            }
        }
    }

    // --- Verificación de Expiración de Sesión ---
    LaunchedEffect(key1 = sessionManager) { // Re-ejecutar si sessionManager cambia (poco probable)
        if (sessionManager.isSessionExpired()) {
            Log.w("HomeScreen", "Session expired based on local check (12 hours). Logging out.")
            // Ya no necesitamos lanzar viewModel.logout() aquí si el ViewModel lo hace en init
            // Solo necesitamos navegar fuera.
            onLogout() // Navegar a Login
        } else {
            Log.d("HomeScreen", "Session is active on composition.")
        }
    }
    // --- Fin Verificación ---

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis Recetas") },
                actions = {
                    IconButton(
                        onClick = { viewModel.loadPrescriptions() },
                        enabled = !uiState.isLoading // Deshabilitar mientras carga
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Actualizar"
                        )
                    }
                    IconButton(onClick = {
                        // Lanzar logout en un scope porque es suspend fun
                        scope.launch {
                            viewModel.logout() // Limpiar datos
                            onLogout()         // Navegar a Login
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Cerrar sesión"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // --- Estado de Carga Inicial ---
            if (uiState.isLoading && uiState.prescriptions.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(50.dp)
                        .align(Alignment.Center)
                )
            }
            // --- Estado Vacío (después de cargar) ---
            else if (uiState.prescriptions.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No tienes recetas médicas",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Cuando tu médico te prescriba medicamentos, aparecerán aquí.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.loadPrescriptions() },
                        enabled = !uiState.isLoading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Volver a intentar")
                    }
                }
            }
            // --- Lista de recetas ---
            else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp) // Padding lateral para la lista
                ) {
                    Text(
                        text = "Recetas Activas",
                        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp), // Ajustar padding
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )

                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(uiState.prescriptions, key = { it.id }) { prescription ->
                            PrescriptionCard(
                                prescription = prescription,
                                onClick = {
                                    // Verificar sesión antes de navegar a detalles
                                    if (!sessionManager.isSessionExpired()) {
                                        onPrescriptionClick(prescription.id, prescription.diagnosis)
                                    } else {
                                        Log.w("HomeScreen", "Clicked prescription but session expired. Logging out.")
                                        scope.launch{ viewModel.logout() } // Limpiar por si acaso
                                        onLogout() // Forzar logout
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp)) // Espacio entre cards
                        }
                    }
                }
            } // Fin else (lista de recetas)
        } // Fin Box principal
    } // Fin Scaffold
}