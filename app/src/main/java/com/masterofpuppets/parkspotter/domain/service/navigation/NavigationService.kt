package com.masterofpuppets.parkspotter.domain.service.navigation

import android.content.Context
import com.masterofpuppets.parkspotter.domain.model.InstalledNavApp

interface NavigationService {
    /**
     * Devolve dinamicamente a lista de todas as apps de mapas/navegação instaladas no aparelho.
     */
    fun getInstalledNavigationApps(context: Context): List<InstalledNavApp>

    /**
     * Tenta iniciar navegação direta para o local de destino especificado.
     */
    fun navigateDirect(
        context: Context,
        destLat: Double,
        destLon: Double,
        destName: String?,
        targetPackageName: String?
    ): Boolean

    /**
     * Tenta iniciar navegação multi-paragem (origem -> waypoints -> destino).
     */
    fun navigateTrip(
        context: Context,
        originLat: Double,
        originLon: Double,
        stops: List<Pair<Double, Double>>,
        destLat: Double,
        destLon: Double,
        destName: String?,
        targetPackageName: String?
    ): Boolean

    /**
     * Copia as coordenadas para a área de transferência do sistema como fallback seguro.
     */
    fun copyCoordinatesToClipboard(
        context: Context,
        lat: Double,
        lon: Double
    )
}
