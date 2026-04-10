import React from 'react';
import { Shield } from 'lucide-react';

const PrivacyPolicy: React.FC = () => {
    return (
        <div className="min-h-screen bg-slate-950 text-slate-300 p-8 flex justify-center selection:bg-emerald-500/30">
            <div className="max-w-3xl w-full">
                <div className="flex items-center gap-4 mb-12 border-b border-white/10 pb-6">
                    <Shield className="w-10 h-10 text-emerald-400" />
                    <h1 className="text-3xl font-black text-white tracking-tight">Privacy Policy</h1>
                </div>

                <div className="space-y-8 text-lg leading-relaxed">
                    <section>
                        <h2 className="text-xl font-bold text-white mb-3">1. Information We Collect</h2>
                        <p>
                            Bounded ("we", "our", or "us") only collects the minimal amount of information required to provide our service. When you use Google Sign-In, we collect your basic profile information (Email Address, Display Name, and Profile Picture) directly from Google to establish your account identity.
                        </p>
                    </section>

                    <section>
                        <h2 className="text-xl font-bold text-white mb-3">2. How We Use Your Information</h2>
                        <p>
                            We use the information collected from Google exclusively to authenticate your account and securely map your private database objects (Card collections, Decks, and Binders) so they can sync seamlessly across your devices. We do not use your email for marketing, and we do not sell your personal data.
                        </p>
                    </section>

                    <section>
                        <h2 className="text-xl font-bold text-white mb-3">3. Data Storage and Security</h2>
                        <p>
                            All user data (including your authentication identity and saved cards) is securely hosted in Google Firebase. We employ strict database security rules that isolate your data, ensuring that no scripts or users can access, read, or modify your personal collections.
                        </p>
                    </section>

                    <section>
                        <h2 className="text-xl font-bold text-white mb-3">4. Data Deletion Rights</h2>
                        <p>
                            You have the right to request the deletion of your account and all associated data at any time. Because Bounded is currently a hobby application, you can initiate data deletion by contacting the developer directly at the support email provided on our Google OAuth consent screen.
                        </p>
                    </section>

                    <div className="mt-16 text-sm text-slate-500">
                        Last Updated: {new Date().toLocaleDateString()}
                    </div>
                </div>
            </div>
        </div>
    );
};

export default PrivacyPolicy;
