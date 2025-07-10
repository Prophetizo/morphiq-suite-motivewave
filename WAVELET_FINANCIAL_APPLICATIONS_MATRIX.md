# Wavelet Financial Applications Matrix

## Complete Wavelet Application Guide for Trading and Financial Analysis

### Quick Reference Legend
- **⚡** = Ultra-fast computation (HFT suitable)
- **🎯** = High precision
- **📊** = Best for visualization
- **🔄** = Symmetric/Phase-preserving
- **📈** = Trend-focused
- **📉** = Volatility-focused
- **🌊** = Cycle detection
- **💥** = Event/Spike detection

---

## Discrete Wavelets (68 Total)

### Haar Family (2 wavelets)

| Wavelet | Characteristics | Best Applications | Trading Strategies | Computation |
|---------|----------------|-------------------|-------------------|-------------|
| **Haar1** | ⚡💥 Step function, sharpest | • Support/Resistance breaks<br>• Volume spikes<br>• Order flow imbalances<br>• Regime changes | • Breakout trading<br>• News trading<br>• Scalping<br>• Market microstructure | O(n), Fastest |
| **Haar1Orthogonal** | ⚡💥🔄 Orthogonal variant | • Bid/Ask spread analysis<br>• Market depth changes<br>• HFT signals<br>• Liquidity detection | • Market making<br>• Statistical arbitrage<br>• Pairs trading | O(n), Fastest |

### Daubechies Family (20 wavelets)

| Wavelet | Characteristics | Best Applications | Trading Strategies | Computation |
|---------|----------------|-------------------|-------------------|-------------|
| **Daubechies2** | ⚡ Minimal smoothing | • Tick data analysis<br>• Micro price movements<br>• Short-term momentum | • HFT<br>• Scalping<br>• Rebate trading | O(n log n) |
| **Daubechies3** | ⚡ Slight smoothing | • 1-min bar analysis<br>• Quick reversals<br>• Intraday volatility | • Day trading<br>• Range trading | O(n log n) |
| **Daubechies4** | 🎯 Balanced | • 5-min patterns<br>• Intraday trends<br>• Noise reduction | • Intraday swing<br>• Momentum trading | O(n log n) |
| **Daubechies5** | 🎯 Good smoothing | • 15-min analysis<br>• Short swings<br>• Pattern recognition | • Short-term swing<br>• Pattern trading | O(n log n) |
| **Daubechies6** | 🎯📈 Trend-preserving | • Hourly analysis<br>• Trend detection<br>• Smooth momentum | • Swing trading<br>• Trend following | O(n log n) |
| **Daubechies7-8** | 📈 Enhanced smoothing | • 4-hour patterns<br>• Multi-day swings<br>• Volatility cycles | • Position entry<br>• Swing trading | O(n log n) |
| **Daubechies9-10** | 📈 Strong smoothing | • Daily analysis<br>• Weekly patterns<br>• Trend strength | • Position trading<br>• Portfolio timing | O(n log n) |
| **Daubechies11-12** | 📈🎯 Very smooth | • Weekly trends<br>• Sector rotation<br>• Long volatility | • Strategic allocation<br>• Hedging | O(n log n) |
| **Daubechies13-16** | 📈 Ultra-smooth | • Monthly cycles<br>• Economic trends<br>• Risk cycles | • Macro trading<br>• Asset allocation | O(n log n) |
| **Daubechies17-20** | 📈 Maximum smoothing | • Quarterly/Yearly<br>• Long-term cycles<br>• Secular trends | • Buy & hold timing<br>• Pension rebalancing | O(n log n) |

### Symlet Family (19 wavelets)

| Wavelet | Characteristics | Best Applications | Trading Strategies | Computation |
|---------|----------------|-------------------|-------------------|-------------|
| **Symlet2-3** | 🔄⚡ Near-symmetric, fast | • Spread analysis<br>• Pairs deviation<br>• Arbitrage signals | • Pairs trading<br>• Stat arb<br>• Convergence trades | O(n log n) |
| **Symlet4-5** | 🔄🎯 Balanced symmetry | • Cross-asset correlation<br>• Relative value<br>• Sector analysis | • Sector rotation<br>• RV trading<br>• Basket trading | O(n log n) |
| **Symlet6-8** | 🔄📈 Symmetric trends | • Index arbitrage<br>• ETF pricing<br>• Factor analysis | • Index arb<br>• Smart beta<br>• Factor investing | O(n log n) |
| **Symlet9-12** | 🔄📈🎯 High symmetry | • Portfolio optimization<br>• Risk parity<br>• Correlation matrices | • Risk parity<br>• Vol targeting<br>• Portfolio construction | O(n log n) |
| **Symlet13-16** | 🔄📈 Very symmetric | • Cross-border arb<br>• Currency hedging<br>• International correlation | • Global macro<br>• Currency overlay<br>• International diversification | O(n log n) |
| **Symlet17-20** | 🔄📈 Maximum symmetry | • Long-term correlation<br>• Structural breaks<br>• Regime persistence | • Strategic allocation<br>• Regime-based investing | O(n log n) |

### Coiflet Family (5 wavelets)

| Wavelet | Characteristics | Best Applications | Trading Strategies | Computation |
|---------|----------------|-------------------|-------------------|-------------|
| **Coiflet1** | 🌊 Basic vanishing moments | • Short cycles<br>• Intraday patterns<br>• Mean reversion | • Mean reversion<br>• Channel trading | O(n log n) |
| **Coiflet2** | 🌊🎯 Better moments | • Daily cycles<br>• Volatility patterns<br>• Options gamma | • Gamma scalping<br>• Vol trading | O(n log n) |
| **Coiflet3** | 🌊📈 Smooth cycles | • Weekly cycles<br>• Trend channels<br>• Smooth volatility | • Trend following<br>• Vol surfaces | O(n log n) |
| **Coiflet4** | 🌊📈🎯 High moments | • Monthly patterns<br>• Business cycles<br>• Term structure | • Curve trading<br>• Calendar spreads | O(n log n) |
| **Coiflet5** | 🌊📈 Maximum moments | • Long cycles<br>• Economic cycles<br>• Credit cycles | • Credit trading<br>• Macro cycles | O(n log n) |

### Biorthogonal Family (15 wavelets)

| Wavelet | Characteristics | Best Applications | Trading Strategies | Computation |
|---------|----------------|-------------------|-------------------|-------------|
| **Bior1.1, 1.3, 1.5** | 🔄⚡ Linear phase, fast | • Spread trading<br>• Arbitrage timing<br>• Cross-market analysis | • Statistical arb<br>• Pairs trading<br>• Merger arb | O(n log n) |
| **Bior2.2, 2.4, 2.6, 2.8** | 🔄🎯 Symmetric analysis | • Correlation trading<br>• Basket decomposition<br>• Factor neutrality | • Market neutral<br>• Long/short equity<br>• Factor neutral | O(n log n) |
| **Bior3.1, 3.3, 3.5, 3.7, 3.9** | 🔄📈 Perfect reconstruction | • Index replication<br>• ETF arbitrage<br>• Systematic strategies | • Index arbitrage<br>• Systematic trading<br>• Smart beta | O(n log n) |
| **Bior4.4** | 🔄📈🎯 High order | • Complex derivatives<br>• Structured products<br>• Exotic options | • Derivatives trading<br>• Structured products | O(n log n) |
| **Bior5.5, 6.8** | 🔄📈 Maximum order | • Long-term hedging<br>• Portfolio insurance<br>• Tail risk | • Tail hedging<br>• Portfolio insurance | O(n log n) |

### Legendre Family (3 wavelets)

| Wavelet | Characteristics | Best Applications | Trading Strategies | Computation |
|---------|----------------|-------------------|-------------------|-------------|
| **Legendre1** | 📊 Polynomial basis | • Linear pricing<br>• Simple derivatives<br>• Basic curves | • Delta hedging<br>• Linear strategies | O(n log n) |
| **Legendre2** | 📊🎯 Quadratic | • Options pricing<br>• Volatility smile<br>• Gamma exposure | • Options trading<br>• Vol arbitrage | O(n log n) |
| **Legendre3** | 📊📈 Cubic | • Complex derivatives<br>• Yield curves<br>• Credit curves | • Curve trading<br>• Credit strategies | O(n log n) |

### Special Wavelets (4 wavelets)

| Wavelet | Characteristics | Best Applications | Trading Strategies | Computation |
|---------|----------------|-------------------|-------------------|-------------|
| **DiscreteMeyer** | 🌊📊 Frequency-defined | • Cycle extraction<br>• Harmonic analysis<br>• Multi-timeframe | • Cycle trading<br>• Harmonic patterns | O(n log n) |
| **Battle23** | 💥 Non-linear | • Chaos detection<br>• Crash prediction<br>• Non-linear dynamics | • Tail risk<br>• Crisis alpha | O(n log n) |
| **CDF5/3** | ⚡ Fast computation | • Real-time analysis<br>• HFT signals<br>• Quick decisions | • HFT<br>• Latency arbitrage | O(n log n) |
| **CDF9/7** | 🎯 High quality | • Precise analysis<br>• Research<br>• Backtesting | • Research<br>• Strategy development | O(n log n) |

---

## Continuous Wavelets (6 Total)

### CWT-Specific Wavelets

| Wavelet | Characteristics | Best Applications | Trading Strategies | Computation |
|---------|----------------|-------------------|-------------------|-------------|
| **Morlet** | 🌊📊📈 Complex, Gaussian | • Market cycles (all scales)<br>• Dominant frequency<br>• Time-varying volatility<br>• Regime detection | • Multi-scale trading<br>• Adaptive strategies<br>• Vol regime trading | O(n²) |
| **Mexican Hat** | 💥📊 2nd derivative Gaussian | • Flash crashes<br>• Liquidity events<br>• Volume spikes<br>• Market shocks | • Event trading<br>• Crisis alpha<br>• Liquidity provision | O(n²) |
| **DOG** | 💥🎯 Derivative of Gaussian | • Precise breakouts<br>• Support/resistance<br>• Trend changes<br>• Momentum shifts | • Breakout trading<br>• Technical trading<br>• Momentum strategies | O(n²) |
| **Paul** | 🔄🌊 Complex analytic | • Phase relationships<br>• Lead/lag analysis<br>• Correlation dynamics<br>• Arbitrage windows | • Arbitrage trading<br>• Correlation trading<br>• Cross-asset strategies | O(n²) |
| **Meyer** | 🌊📊🎯 Smooth frequency | • Clean decomposition<br>• Noise-free trends<br>• Perfect bands<br>• Harmonic patterns | • Harmonic trading<br>• Clean signals<br>• Multi-timeframe | O(n²) |

---

## Application-Specific Selection Guide

### By Market Condition

| Condition | Best Discrete | Best Continuous | Combination Strategy |
|-----------|--------------|-----------------|---------------------|
| **Trending** | Sym8-12, DB6-10, Coif3 | Morlet, Meyer | DWT denoising → CWT trend |
| **Volatile** | Haar, DB2-3, Bior1.1 | Mexican Hat, DOG | CWT events → DWT entry |
| **Ranging** | DB4-6, Bior2.2-2.4 | Paul, Meyer | DWT levels → CWT cycles |
| **Quiet** | DB12-20, Sym12-20 | Morlet (large scales) | DWT smooth → CWT regime |
| **Transitioning** | Haar, Battle23 | DOG, Mexican Hat | CWT detection → DWT confirm |

### By Asset Class

| Asset | Primary | Secondary | CWT Choice | Strategy Focus |
|-------|---------|-----------|------------|----------------|
| **Stocks** | Sym6-8 | DB6-8 | Morlet | Trend + cycles |
| **Forex** | Bior3.5 | Coif3 | Paul | Correlation + arbitrage |
| **Futures** | DB4-6 | Sym4-6 | Meyer | Clean trends + rolls |
| **Options** | Coif2-4 | Legendre2-3 | Morlet | Volatility + Greeks |
| **Crypto** | Haar | DB2-3 | Mexican Hat | Spikes + volatility |
| **Bonds** | Legendre1-3 | DB12-16 | Paul | Curves + duration |
| **Commodities** | DB6-10 | Bior2.4 | DOG | Seasonality + trends |

### By Trading Frequency

| Frequency | Wavelets | Key Focus | Optimal Combo |
|-----------|----------|-----------|---------------|
| **HFT (<1min)** | Haar, DB2, CDF5/3 | Speed > accuracy | Haar + Mexican Hat |
| **Scalping (1-5min)** | DB2-3, Haar | Quick signals | DB2 + DOG |
| **Day Trading** | DB4-6, Sym4-6 | Balance | DB4 + Morlet |
| **Swing (days)** | DB6-10, Sym6-10 | Smooth trends | Sym8 + Meyer |
| **Position (weeks)** | DB10-16, Coif3-4 | Long trends | DB12 + Morlet |
| **Investment** | DB16-20, Sym16-20 | Very smooth | Sym16 + Paul |

### By Strategy Type

| Strategy | Best Wavelets | CWT Addition | Implementation |
|----------|--------------|--------------|----------------|
| **Trend Following** | Sym8-12, DB8-12 | Morlet | Multi-scale trends |
| **Mean Reversion** | DB4-6, Coif1-2 | Meyer | Clean bands |
| **Arbitrage** | Bior2.2-3.5 | Paul | Phase analysis |
| **Volatility** | Coif2-4, DB6 | Morlet | Vol regimes |
| **Pattern** | Sym4-8, DB4-8 | DOG | Precise points |
| **Event** | Haar, Mexican Hat | Mexican Hat | Spike detection |
| **Momentum** | DB3-6, Sym3-6 | DOG | Direction changes |
| **Pairs** | Bior series | Paul | Correlation |

---

## Performance vs. Accuracy Trade-offs

### Speed Ranking (Fastest to Slowest)
1. **Haar** - O(n), minimal operations
2. **CDF5/3** - Optimized for speed
3. **DB2-DB4** - Few coefficients
4. **Sym2-Sym4** - Slightly more ops
5. **Bior1.X series** - Dual filters
6. **DB6-DB10** - More coefficients
7. **Coiflets** - Vanishing moments
8. **DB12-DB20** - Many coefficients
9. **Legendre** - Polynomial ops
10. **CWT wavelets** - O(n²) for full analysis

### Accuracy Ranking (Most to Least Precise)
1. **CWT wavelets** - Continuous analysis
2. **DB16-DB20** - Maximum smoothing
3. **Sym16-Sym20** - High symmetry
4. **Coif4-Coif5** - Many vanishing moments
5. **Bior4.4-6.8** - Perfect reconstruction
6. **Meyer** - Frequency precision
7. **DB8-DB12** - Good balance
8. **DB4-DB6** - Practical accuracy
9. **DB2-DB3** - Basic accuracy
10. **Haar** - Minimal, but precise for edges

---

## Implementation Priority Matrix

### Must-Have (Core Set)
1. **Haar** - Breakout detection
2. **DB4** - General purpose
3. **DB8** - Smooth trends
4. **Sym8** - Symmetric analysis
5. **Morlet (CWT)** - Time-frequency

### Should-Have (Enhanced Set)
6. **DB2** - HFT/Scalping
7. **Bior3.5** - Pairs trading
8. **Coif3** - Volatility
9. **Mexican Hat (CWT)** - Events
10. **Paul (CWT)** - Phase analysis

### Nice-to-Have (Specialized)
11. **DB12-16** - Long-term
12. **Sym12-16** - Portfolio
13. **Meyer** - Frequency purity
14. **Legendre2** - Options
15. **DOG (CWT)** - Precision breaks

This matrix provides a complete guide for selecting the optimal wavelet(s) for any financial analysis task, considering computational constraints, accuracy requirements, and specific market characteristics.