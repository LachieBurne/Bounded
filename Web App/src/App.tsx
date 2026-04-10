import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { CardProvider } from './context/CardContext';
import { DeckProvider } from './context/DeckContext';
import { PriceProvider } from './context/PriceContext';
import { ProtectedLayout } from './components/layout/ProtectedLayout';
import Login from './pages/Login';
import BinderPage from './pages/Binder';
import DeckList from './pages/DeckList';
import DeckEditor from './pages/DeckEditor';
import PrivacyPolicy from './pages/PrivacyPolicy';
import TermsOfService from './pages/TermsOfService';

function App() {
  return (
    <AuthProvider>
      <CardProvider>
        <PriceProvider>
          <DeckProvider>
            <Router>
              <Routes>
                {/* Public Routes */}
                <Route path="/login" element={<Login />} />
                <Route path="/privacy" element={<PrivacyPolicy />} />
                <Route path="/terms" element={<TermsOfService />} />

                {/* Protected Routes */}
                <Route element={<ProtectedLayout />}>
                  <Route path="/" element={<BinderPage />} />
                  <Route path="/decks" element={<DeckList />} />
                  <Route path="/decks/:deckId" element={<DeckEditor />} />
                  <Route path="*" element={<Navigate to="/" replace />} />
                </Route>

              </Routes>
            </Router>
          </DeckProvider>
        </PriceProvider>
      </CardProvider>
    </AuthProvider>
  );
}

export default App;
