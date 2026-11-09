import React, { createContext, useContext, useEffect, useState } from 'react';

interface PriceContextType {
    prices: Record<string, number>;
    currency: string;
    exchangeRates: Record<string, number>;
    loading: boolean;
    setCurrency: (code: string) => void;
    convertPrice: (usdPrice: number) => number;
    getCurrencySymbol: () => string;
    /** A US dollar price in the chosen currency, e.g. "$12.40". */
    formatPrice: (usdPrice: number) => string;
}

// eslint-disable-next-line react-refresh/only-export-components
export const CURRENCIES: { code: string; name: string }[] = [
    { code: 'USD', name: 'US dollar' },
    { code: 'AUD', name: 'Australian dollar' },
    { code: 'EUR', name: 'Euro' },
    { code: 'GBP', name: 'British pound' },
    { code: 'CAD', name: 'Canadian dollar' },
    { code: 'NZD', name: 'New Zealand dollar' },
    { code: 'JPY', name: 'Japanese yen' },
];

const PriceContext = createContext<PriceContextType>({} as PriceContextType);

// eslint-disable-next-line react-refresh/only-export-components
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
                const res = await fetch('https://lachieburne.github.io/Bounded/prices.json');
                const data = await res.json();
                setPrices(data);
            } catch (e) {
                console.error("Failed to fetch USD card prices.", e);
            }
        };

        const fetchRates = async () => {
            try {
                const res = await fetch('https://api.frankfurter.dev/v1/latest?base=USD');
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

    const formatPrice = (usdPrice: number): string => {
        const digits = currency === 'JPY' ? 0 : 2;
        return getCurrencySymbol() + convertPrice(usdPrice).toLocaleString('en-US', { minimumFractionDigits: digits, maximumFractionDigits: digits });
    };

    const value = {
        prices,
        currency,
        exchangeRates,
        loading,
        setCurrency,
        convertPrice,
        getCurrencySymbol,
        formatPrice
    };

    return (
        <PriceContext.Provider value={value}>
            {children}
        </PriceContext.Provider>
    );
};
