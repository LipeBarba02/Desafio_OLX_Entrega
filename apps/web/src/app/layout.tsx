import type { ReactNode } from "react";
export const metadata = { title: "OLX Enterprise Lab | Anúncios", description: "Interface de demonstração do marketplace educacional" };
export default function RootLayout({children}:{children:ReactNode}) {
  return <html lang="pt-BR"><body>{children}</body></html>;
}
