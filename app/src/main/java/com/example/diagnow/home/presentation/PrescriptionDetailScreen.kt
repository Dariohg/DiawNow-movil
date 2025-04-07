package com.example.diagnow.home.presentation

import android.app.Application // <-- Importar Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel // <-- Importar viewModel composable
import com.example.diagnow.DiagNowApplication
import com.example.diagnow.core.database.entity.TreatmentStatus
import com.example.diagnow.core.database.repository.LocalDataRepository
import com.example.diagnow.core.network.RetrofitHelper
import com.example.diagnow.core.session.SessionManager
import com.example.diagnow.home.data.model.MedicationDetailResponse
import com.example.diagnow.home.data.repository.PrescriptionRepository
import com.example.diagnow.home.domain.GetPrescriptionMedicationsUseCase
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrescriptionDetailScreen(
    prescriptionId: String,
    prescriptionDiagnosis: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    // --- Dependencias (igual que antes) ---
    val sessionManager = remember { SessionManager(context) }
    val retrofitHelper = remember { RetrofitHelper(sessionManager) }
    val database = remember { (context.applicationContext as DiagNowApplication).database }
    val prescriptionDao = remember { database.prescriptionDao() }
    val medicationDao = remember { database.medicationDao() }
    val localRepository = remember { LocalDataRepository(database, prescriptionDao, medicationDao) }

    // --- ViewModel con sus dependencias (USANDO LA FACTORY) ---
    val application = LocalContext.current.applicationContext as Application // Obtener Application
    val viewModel: PrescriptionDetailViewModel = viewModel( // Usar viewModel() composable
        key = prescriptionId, // Opcional: clave para recrear si cambia ID
        factory = PrescriptionDetailViewModelFactory( // Pasar la Factory
            application = application,
            getPrescriptionMedicationsUseCase = remember { // Recordar el UseCase
                GetPrescriptionMedicationsUseCase(
                    remoteRepository = PrescriptionRepository(retrofitHelper, sessionManager),
                    localRepository = localRepository
                )
            },
            localRepository = localRepository
        )
    )
    // --- Fin instanciación ViewModel ---

    // --- Carga inicial ---
    LaunchedEffect(prescriptionId) {
        viewModel.loadPrescriptionMedications(prescriptionId)
    }

    // --- Observar estado de la UI ---
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // --- Manejar errores con Snackbar ---
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError() // Limpiar el error después de mostrarlo
        }
    }

    // --- Estructura de la pantalla ---
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle de Receta") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Aplicar padding del Scaffold
        ) {
            // --- Estado de carga ---
            if (uiState.isLoading && uiState.medications.isEmpty()) { // Mostrar solo si no hay datos aún
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(50.dp)
                        .align(Alignment.Center)
                )
            } else {
                // --- Contenido principal ---
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp) // Padding horizontal general
                ) {
                    // --- Card de Diagnóstico ---
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp), // Padding superior
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Diagnóstico",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = prescriptionDiagnosis.takeIf { it.isNotBlank() } ?: "No especificado",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- Sección de Medicamentos ---
                    // Mostrar sección incluso si se está recargando en segundo plano
                    if (uiState.medications.isEmpty() && !uiState.isLoading) {
                        // Mensaje si realmente no hay medicamentos después de cargar
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No se encontraron medicamentos para esta receta.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // Título de la sección
                        Text(
                            text = "Medicamentos",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(vertical = 8.dp) // Espacio vertical para el título
                        )

                        // Indicador de carga sutil si se está recargando
                        if (uiState.isLoading) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                            // O puedes usar un pequeño CircularProgressIndicator alineado
                        }

                        // --- Lista de Medicamentos ---
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 16.dp) // Padding inferior para la lista
                        ) {
                            items(items = uiState.medications, key = { it.id }) { medication ->
                                MedicationDetailCard(
                                    medication = medication,
                                    onStartClick = { viewModel.startTreatment(medication.id) },
                                    onEndClick = { viewModel.endTreatment(medication.id) }
                                )
                                Spacer(modifier = Modifier.height(12.dp)) // Espacio entre cards
                            }
                        } // Fin LazyColumn
                    } // Fin else (hay medicamentos o se está cargando)
                } // Fin Column contenido principal
            } // Fin else (no está en carga inicial)
        } // Fin Box principal
    } // Fin Scaffold
}


// --- MedicationDetailCard (sin cambios respecto a la versión anterior) ---
@Composable
fun MedicationDetailCard(
    medication: MedicationDetailResponse,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Medication,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = medication.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Divider()
            Spacer(modifier = Modifier.height(12.dp))

            MedicationDetailRow("Dosis", medication.dosage)
            val frequencyText = when (medication.frequency) {
                1 -> "Cada hora"
                24 -> "Una vez al día"
                else -> "Cada ${medication.frequency} horas"
            }
            MedicationDetailRow("Frecuencia", frequencyText)
            MedicationDetailRow("Duración", "${medication.days} días")
            medication.administrationRoute?.takeIf { it.isNotBlank() }?.let {
                MedicationDetailRow("Vía", it)
            }
            medication.instructions?.takeIf { it.isNotBlank() }?.let {
                MedicationDetailRow("Instrucciones", it)
            }

            if (medication.treatmentStatus == TreatmentStatus.ACTIVE || medication.treatmentStatus == TreatmentStatus.COMPLETED) {
                medication.treatmentStartDate?.let { startDate ->
                    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
                    MedicationDetailRow("Inicio Tratamiento", dateFormat.format(startDate))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                when (medication.treatmentStatus) {
                    TreatmentStatus.NOT_STARTED -> {
                        OutlinedButton(
                            onClick = onStartClick,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Iniciar", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Iniciar Tratamiento")
                        }
                    }
                    TreatmentStatus.ACTIVE -> {
                        OutlinedButton(
                            onClick = onEndClick,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Finalizar", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Finalizar Tratamiento", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TreatmentStatus.COMPLETED -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Completado", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Tratamiento Finalizado", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }
        }
    }
}

// --- MedicationDetailRow (sin cambios respecto a la versión anterior) ---
@Composable
fun MedicationDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1.5f)
        )
    }
}