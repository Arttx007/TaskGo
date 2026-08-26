package com.example.Estrela.Service;

import org.springframework.stereotype.Service;

/**
 * Cálculo de distância entre coordenadas (US-03) via fórmula de Haversine, em memória — sem
 * PostGIS/hibernate-spatial, para manter a query portável entre Postgres (dev) e H2 (testes) em
 * um volume de dados de portfólio.
 */
@Service
public class GeoService {

    private static final double RAIO_TERRA_KM = 6371.0;

    /**
     * Distância em quilômetros entre dois pontos geográficos.
     *
     * @param lat1 latitude do primeiro ponto
     * @param lon1 longitude do primeiro ponto
     * @param lat2 latitude do segundo ponto
     * @param lon2 longitude do segundo ponto
     * @return distância em km
     */
    public double distanciaKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return RAIO_TERRA_KM * c;
    }
}
