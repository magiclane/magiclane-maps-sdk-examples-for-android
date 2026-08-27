/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapscatalog

/**
 * Maps ISO 3166-1 alpha-3 and alpha-2 country codes to continent names, used to organize
 * a road-map catalog by continent.
 */
object ContinentMapper {

    private val countryToContinent: Map<String, String> = buildMap {
        fun continent(name: String, vararg codes: String) = codes.forEach { put(it, name) }

        continent(
            "Europe",
            "ALB", "AL", "AND", "AD", "AUT", "AT", "BLR", "BY", "BEL", "BE", "BIH", "BA",
            "BGR", "BG", "HRV", "HR", "CYP", "CY", "CZE", "CZ", "DNK", "DK", "EST", "EE",
            "FRO", "FO", "FIN", "FI", "FRA", "FR", "DEU", "DE", "GIB", "GI", "GRC", "GR",
            "GGY", "GG", "HUN", "HU", "ISL", "IS", "IRL", "IE", "IMN", "IM", "ITA", "IT",
            "JEY", "JE", "LVA", "LV", "LIE", "LI", "LTU", "LT", "LUX", "LU", "MLT", "MT",
            "MDA", "MD", "MCO", "MC", "MNE", "ME", "NLD", "NL", "MKD", "MK", "NOR", "NO",
            "POL", "PL", "PRT", "PT", "ROU", "RO", "SMR", "SM", "SRB", "RS", "SVK", "SK",
            "SVN", "SI", "ESP", "ES", "SWE", "SE", "CHE", "CH", "UKR", "UA", "GBR", "GB",
            "VAT", "VA", "ALA", "AX", "SJM", "SJ", "XKX", "XK", "RUS", "RU",
        )
        continent(
            "Asia",
            "AFG", "AF", "ARM", "AM", "AZE", "AZ", "BHR", "BH", "BGD", "BD", "BTN", "BT",
            "BRN", "BN", "KHM", "KH", "CHN", "CN", "GEO", "GE", "HKG", "HK", "IND", "IN",
            "IDN", "ID", "IRN", "IR", "IRQ", "IQ", "ISR", "IL", "JPN", "JP", "JOR", "JO",
            "KAZ", "KZ", "PRK", "KP", "KOR", "KR", "KWT", "KW", "KGZ", "KG", "LAO", "LA",
            "LBN", "LB", "MAC", "MO", "MYS", "MY", "MDV", "MV", "MNG", "MN", "MMR", "MM",
            "NPL", "NP", "OMN", "OM", "PAK", "PK", "PSE", "PS", "PHL", "PH", "QAT", "QA",
            "SAU", "SA", "SGP", "SG", "LKA", "LK", "SYR", "SY", "TWN", "TW", "TJK", "TJ",
            "THA", "TH", "TLS", "TL", "TUR", "TR", "TKM", "TM", "ARE", "AE", "UZB", "UZ",
            "VNM", "VN", "YEM", "YE", "CXR", "CX", "CCK", "CC", "IOT", "IO",
        )
        continent(
            "Africa",
            "DZA", "DZ", "AGO", "AO", "BEN", "BJ", "BWA", "BW", "BFA", "BF", "BDI", "BI",
            "CPV", "CV", "CMR", "CM", "CAF", "CF", "TCD", "TD", "COM", "KM", "COG", "CG",
            "COD", "CD", "CIV", "CI", "DJI", "DJ", "EGY", "EG", "GNQ", "GQ", "ERI", "ER",
            "SWZ", "SZ", "ETH", "ET", "GAB", "GA", "GMB", "GM", "GHA", "GH", "GIN", "GN",
            "GNB", "GW", "KEN", "KE", "LSO", "LS", "LBR", "LR", "LBY", "LY", "MDG", "MG",
            "MWI", "MW", "MLI", "ML", "MRT", "MR", "MUS", "MU", "MYT", "YT", "MAR", "MA",
            "MOZ", "MZ", "NAM", "NA", "NER", "NE", "NGA", "NG", "REU", "RE", "RWA", "RW",
            "SHN", "SH", "STP", "ST", "SEN", "SN", "SYC", "SC", "SLE", "SL", "SOM", "SO",
            "ZAF", "ZA", "SSD", "SS", "SDN", "SD", "TZA", "TZ", "TGO", "TG", "TUN", "TN",
            "UGA", "UG", "ESH", "EH", "ZMB", "ZM", "ZWE", "ZW",
        )
        continent(
            "North America",
            "AIA", "AI", "ATG", "AG", "ABW", "AW", "BHS", "BS", "BRB", "BB", "BLZ", "BZ",
            "BMU", "BM", "BES", "BQ", "VGB", "VG", "CAN", "CA", "CYM", "KY", "CRI", "CR",
            "CUB", "CU", "CUW", "CW", "DMA", "DM", "DOM", "DO", "SLV", "SV", "GRL", "GL",
            "GRD", "GD", "GLP", "GP", "GTM", "GT", "HTI", "HT", "HND", "HN", "JAM", "JM",
            "MTQ", "MQ", "MEX", "MX", "MSR", "MS", "NIC", "NI", "PAN", "PA", "PRI", "PR",
            "BLM", "BL", "KNA", "KN", "LCA", "LC", "MAF", "MF", "SPM", "PM", "VCT", "VC",
            "SXM", "SX", "TTO", "TT", "TCA", "TC", "USA", "US", "VIR", "VI",
        )
        continent(
            "South America",
            "ARG", "AR", "BOL", "BO", "BVT", "BV", "BRA", "BR", "CHL", "CL", "COL", "CO",
            "ECU", "EC", "FLK", "FK", "GUF", "GF", "GUY", "GY", "PRY", "PY", "PER", "PE",
            "SGS", "GS", "SUR", "SR", "URY", "UY", "VEN", "VE",
        )
        continent(
            "Oceania",
            "ASM", "AS", "AUS", "AU", "COK", "CK", "FJI", "FJ", "PYF", "PF", "GUM", "GU",
            "KIR", "KI", "MHL", "MH", "FSM", "FM", "NRU", "NR", "NCL", "NC", "NZL", "NZ",
            "NIU", "NU", "NFK", "NF", "MNP", "MP", "PLW", "PW", "PNG", "PG", "PCN", "PN",
            "WSM", "WS", "SLB", "SB", "TKL", "TK", "TON", "TO", "TUV", "TV", "UMI", "UM",
            "VUT", "VU", "WLF", "WF", "HMD", "HM",
        )
        continent("Antarctica", "ATA", "AQ", "ATF", "TF")
    }

    private val continentOrder: Map<String, Int> = listOf(
        "Europe",
        "Asia",
        "Africa",
        "North America",
        "South America",
        "Oceania",
        "Antarctica",
        "Other",
    ).withIndex().associate { (index, name) -> name to index }

    /** Returns the continent name for [countryCode], or `"Other"` when unknown. */
    fun getContinent(countryCode: String): String = countryToContinent[countryCode.uppercase()] ?: "Other"

    /** Returns the display ordering index of [continentName] (Europe first). */
    fun getContinentOrder(continentName: String): Int = continentOrder[continentName] ?: UNKNOWN_ORDER

    private const val UNKNOWN_ORDER = 99
}
