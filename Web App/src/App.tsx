import { BrowserRouter as Router, Routes, Route, Navigate, useLocation, type Location } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { CardProvider } from './context/CardContext';
import { DeckProvider } from './context/DeckContext';
import { PriceProvider } from './context/PriceContext';
import { BinderProvider } from './context/BinderContext';
import { ProtectedLayout } from './components/layout/ProtectedLayout';
import Login from './pages/Login';
import CollectionPage from './pages/Collection';
import CardDetailPage from './pages/CardDetail';
import DeckList from './pages/DeckList';
import DeckEditor from './pages/DeckEditor';
import PrivacyPolicy from './pages/PrivacyPolicy';
import TermsOfService from './pages/TermsOfService';

/**
 * A card opened from a grid carries the page underneath as `background`: that page stays
 * rendered (keeping its filters and scroll) and the card shows on top. A card link opened
 * directly has no background and shows as a page of its own.
 */
function AppRoutes() {
  const location = useLocation();
  const background = (location.state as { background?: Location } | null)?.background;
  return (
    <>
      <Routes location={background ?? location}>
        {/* Public Routes */}
        <Route path="/login" element={<Login />} />
        <Route path="/privacy" element={<PrivacyPolicy />} />
        <Route path="/terms" element={<TermsOfService />} />

        {/* Protected Routes */}
        <Route element={<ProtectedLayout />}>
          <Route path="/" element={<CollectionPage />} />
          <Route path="/binders/:binderId" element={<CollectionPage />} />
          <Route path="/cards/:cardId" element={<CardDetailPage />} />
          <Route path="/decks" element={<DeckList />} />
          <Route path="/decks/:deckId" element={<DeckEditor />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
      {background && (
        <Routes>
          <Route path="/cards/:cardId" element={<CardDetailPage />} />
        </Routes>
      )}
    </>
  );
}

function App() {
  return (
    <AuthProvider>
      <CardProvider>
        <PriceProvider>
          <BinderProvider>
            <DeckProvider>
              <Router>
                <AppRoutes />
              </Router>
            </DeckProvider>
          </BinderProvider>
        </PriceProvider>
      </CardProvider>
    </AuthProvider>
  );
}

export default App;
