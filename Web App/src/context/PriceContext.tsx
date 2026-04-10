import React, { createContext, useContext, useEffect, useState } from 'react';

interface PriceContextType {
    prices: Record<string, number>;
    currency: string;
    exchangeRates: Record<string, number>;
    loading: boolean;
    setCurrency: (code: string) => void;
    convertPrice: (usdPrice: number) => number;
    getCurrencySymbol: () => string;
}

const PriceContext = createContext<PriceContextType>({} as PriceContextType);

export const usePrices = () => useContext(PriceContext);

export const PriceProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [prices, setPrices] = useState<Record<string, number>>({});
    const [exchangeRates, setExchangeRates] = useState<Record<string, number>>({ USD: 1.0 });
    const [currency, setCurrencyState] = useState(() => {
        return localStorage.getItem('riftbinder_currency') || 'USD';
    });
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const fetchPrices = async () => {
            try {
                const res = await fetch('https://lachieburne.github.io/Riftbounded/prices.json');
                const data = await res.json();
                setPrices(data);
            } catch (e) {
                console.error("Failed to fetch USD card prices.", e);
            }
        };

        const fetchRates = async () => {
            try {
                const res = await fetch('https://api.frankfurter.app/latest?from=USD');
                const data = await res.json();
                setExchangeRates({ USD: 1.0, ...data.rates });
            } catch (e) {
                console.error("Failed to fetch exchange rates.", e);
            }
        };

        Promise.all([fetchPrices(), fetchRates()]).finally(() => {
            setLoading(false);
        });
    }, []);

    const setCurrency = (code: string) => {
        localStorage.setItem('riftbinder_currency', code);
        setCurrencyState(code);
    };

    const convertPrice = (usdPrice: number): number => {
        const rate = exchangeRates[currency] || 1.0;
        return usdPrice * rate;
    };

    const getCurrencySymbol = (): string => {
        // Fallback or exact matches mirroring Android
        switch (currency) {
            case 'USD':
            case 'AUD':
            case 'CAD':
            case 'NZD':
                return '$';
            case 'EUR':
                return '€';
            case 'GBP':
                return '£';
            case 'JPY':
                return '¥';
            default:
                return '$';
        }
    };

    const value = {
        prices,
        currency,
        exchangeRates,
        loading,
        setCurrency,
        convertPrice,
        getCurrencySymbol
    };

    return (
        <PriceContext.Provider value={value}>
            {children}
        </PriceContext.Provider>
    );
};
