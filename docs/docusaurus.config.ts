module.exports = {
  title: "CabinJ", // your framework’s name
  tagline: "Lightweight Java Web Framework",
  url: "https://your-domain.com", // change to your production URL
  baseUrl: "/", // so pages live under /docs/*
  favicon: "img/logo.png",
  organizationName: "CabinJV", // for deployment
  projectName: "cabinJ", // repo name

  themeConfig: {
    navbar: {
      title: "CabinJ",
      logo: { alt: "CabinJ Logo", src: "img/logo.png" },
      items: [
        { to: "getting-started/installation", label: "Docs", position: "left" },
        {
          href: "https://github.com/CabinJV/CabinJv",
          label: "GitHub",
          position: "right",
        },
        {
          type: "docsVersionDropdown", // versioning control
          position: "right",
        },
      ],
    },
    footer: {
      links: [
      ],
      copyright: `© ${new Date().getFullYear()} CabinJV`,
    },
    prism: {
      additionalLanguages: ["java"], // enable Java syntax highlighting
    },
  },

  presets: [
    [
      "classic",
      {
        docs: {
          sidebarPath: require.resolve("./sidebars.js"),
          editUrl: "https://github.com/CabinJV/CabinJv/edit/main/docs/",
          routeBasePath: "/", // serve docs at site root under /docs
          includeCurrentVersion: true,
        },
        blog: false,
        theme: { customCss: require.resolve("./src/css/custom.css") },
      },
    ],
  ],
};
