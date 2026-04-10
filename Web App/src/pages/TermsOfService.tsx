import React from 'react';
import { Scale } from 'lucide-react';

const TermsOfService: React.FC = () => {
    return (
        <div className="min-h-screen bg-slate-950 text-slate-300 p-8 flex justify-center selection:bg-emerald-500/30">
            <div className="max-w-3xl w-full">
                <div className="flex items-center gap-4 mb-12 border-b border-white/10 pb-6">
                    <Scale className="w-10 h-10 text-emerald-400" />
                    <h1 className="text-3xl font-black text-white tracking-tight">Terms of Service</h1>
                </div>

                <div className="space-y-8 text-lg leading-relaxed">
                    <section>
                        <h2 className="text-xl font-bold text-white mb-3">1. Acceptance of Terms</h2>
                        <p>
                            By accessing and using Bounded (the "Service"), you accept and agree to be bound by the terms and provisions of this agreement.
                        </p>
                    </section>

                    <section>
                        <h2 className="text-xl font-bold text-white mb-3">2. Description of Service</h2>
                        <p>
                            Bounded is a hobbyist trading card collection tracker and deck-building utility. The Service is provided "as is" and "as available". The developer makes no guarantees regarding the continuous uptime or long-term availability of the platform.
                        </p>
                    </section>

                    <section>
                        <h2 className="text-xl font-bold text-white mb-3">3. Intellectual Property</h2>
                        <p>
                            All card data, names, rules, mechanics, and artwork referenced within the Service are the intellectual property of their respective creators and publishers. Bounded is an independent utility application and is not directly affiliated with, endorsed, sponsored, or specifically approved by any official copyright holder.
                        </p>
                    </section>

                    <section>
                        <h2 className="text-xl font-bold text-white mb-3">4. Limitation of Liability</h2>
                        <p>
                            To the fullest extent permitted by law, Bounded shall not be liable for any indirect, incidental, special, consequential, or punitive damages, or any loss of profits or revenues (whether incurred directly or indirectly), or any loss of data resulting from the use or inability to use the Service.
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

export default TermsOfService;
